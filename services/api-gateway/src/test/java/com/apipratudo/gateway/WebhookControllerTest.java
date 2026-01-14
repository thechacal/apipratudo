package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ConcurrentMap<String, String> IDEMPOTENCY_IDS = new ConcurrentHashMap<>();

  private static MockWebServer quotaServer;
  private static MockWebServer webhookServer;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    if (quotaServer == null) {
      quotaServer = new MockWebServer();
      quotaServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          return new MockResponse()
              .setResponseCode(200)
              .setHeader("Content-Type", "application/json")
              .setBody("{\"allowed\":true,\"limit\":100,\"remaining\":99}");
        }
      });
      try {
        quotaServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start quota mock server", e);
      }
    }

    if (webhookServer == null) {
      webhookServer = new MockWebServer();
      webhookServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          return webhookResponse(request);
        }
      });
      try {
        webhookServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start webhook mock server", e);
      }
    }

    registry.add("quota.base-url", () -> quotaServer.url("/").toString());
    registry.add("quota.timeout-ms", () -> 2000);
    registry.add("quota.internal-token", () -> "test-internal");
    registry.add("webhook.base-url", () -> webhookServer.url("/").toString());
    registry.add("webhook.timeout-ms", () -> 2000);
    registry.add("webhook.service-token", () -> "test-service");
  }

  @AfterAll
  static void shutdownServers() throws IOException {
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
    if (webhookServer != null) {
      webhookServer.shutdown();
    }
  }

  @AfterEach
  void drainRequests() throws InterruptedException {
    if (quotaServer != null) {
      while (quotaServer.takeRequest(50, TimeUnit.MILLISECONDS) != null) {
        // drain quota requests between tests
      }
    }
    if (webhookServer != null) {
      while (webhookServer.takeRequest(50, TimeUnit.MILLISECONDS) != null) {
        // drain webhook requests between tests
      }
    }
  }

  @Test
  void createWebhookReturnsCreated() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.targetUrl").value("https://cliente.exemplo.com/webhooks/apipratudo"))
        .andExpect(jsonPath("$.events[0]").value("invoice.paid"))
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.createdAt").isNotEmpty());
  }

  @Test
  void createWebhookValidationError() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "eventType", "invoice.paid"
    ));

    mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void createWebhookIdempotency() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    String key = "idempotency-" + UUID.randomUUID();

    MvcResult first = mockMvc.perform(post("/v1/webhooks")
            .header("Idempotency-Key", key)
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    MvcResult second = mockMvc.perform(post("/v1/webhooks")
            .header("Idempotency-Key", key)
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
    JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());

    assertThat(firstJson.get("id").asText()).isEqualTo(secondJson.get("id").asText());
    assertThat(firstJson.get("events").get(0).asText()).isEqualTo("invoice.paid");
  }

  @Test
  void createWebhookIdempotencyUsesRemote() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    String key = "remote-" + UUID.randomUUID();

    mockMvc.perform(post("/v1/webhooks")
            .header("Idempotency-Key", key)
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());

    RecordedRequest forwarded = webhookServer.takeRequest(2, TimeUnit.SECONDS);
    assertThat(forwarded).isNotNull();
    assertThat(forwarded.getPath()).isEqualTo("/v1/webhooks");
    assertThat(forwarded.getHeader("X-Api-Key")).isEqualTo("test-key");
    assertThat(forwarded.getHeader("Idempotency-Key")).isEqualTo(key);

    JsonNode forwardedBody = objectMapper.readTree(forwarded.getBody().readUtf8());
    assertThat(forwardedBody.get("targetUrl").asText())
        .isEqualTo("https://cliente.exemplo.com/webhooks/apipratudo");
    assertThat(forwardedBody.get("events").get(0).asText()).isEqualTo("invoice.paid");
  }

  @Test
  void listWebhooksForwardsQueryParams() throws Exception {
    mockMvc.perform(get("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .queryParam("limit", "10")
            .queryParam("cursor", "abc123"))
        .andExpect(status().isOk());

    RecordedRequest forwarded = webhookServer.takeRequest(2, TimeUnit.SECONDS);
    assertThat(forwarded).isNotNull();
    assertThat(forwarded.getPath()).isEqualTo("/v1/webhooks?limit=10&cursor=abc123");
    assertThat(forwarded.getHeader("X-Api-Key")).isEqualTo("test-key");
  }

  @Test
  void getWebhookForwardsPath() throws Exception {
    mockMvc.perform(get("/v1/webhooks/{id}", "wh-123")
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isOk());

    RecordedRequest forwarded = webhookServer.takeRequest(2, TimeUnit.SECONDS);
    assertThat(forwarded).isNotNull();
    assertThat(forwarded.getPath()).isEqualTo("/v1/webhooks/wh-123");
    assertThat(forwarded.getHeader("X-Api-Key")).isEqualTo("test-key");
  }

  @Test
  void getWebhookNotFound() throws Exception {
    mockMvc.perform(get("/v1/webhooks/nao-existe")
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void updateWebhookChangesFields() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    MvcResult created = mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
        .andExpect(status().isCreated())
        .andReturn();

    String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    String patchBody = objectMapper.writeValueAsString(Map.of(
        "status", "DISABLED",
        "eventType", "invoice.failed"
    ));

    mockMvc.perform(patch("/v1/webhooks/{id}", id)
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("DISABLED"))
        .andExpect(jsonPath("$.eventType").value("invoice.failed"));
  }

  @Test
  void deleteWebhookMarksDeleted() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    MvcResult created = mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
        .andExpect(status().isCreated())
        .andReturn();

    String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc.perform(delete("/v1/webhooks/{id}", id)
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/v1/webhooks/{id}", id)
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isNotFound());
  }

  @Test
  void testWebhookCreatesDelivery() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    MvcResult created = mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
        .andExpect(status().isCreated())
        .andReturn();

    String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc.perform(post("/v1/webhooks/{id}/test", id)
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.deliveryId").isNotEmpty())
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void testWebhookPublishesEvent() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    MvcResult created = mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
        .andExpect(status().isCreated())
        .andReturn();

    String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    MvcResult testDelivery = mockMvc.perform(post("/v1/webhooks/{id}/test", id)
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isCreated())
        .andReturn();

    String deliveryId = objectMapper.readTree(testDelivery.getResponse().getContentAsString())
        .get("deliveryId").asText();

    RecordedRequest createRequest = webhookServer.takeRequest(2, TimeUnit.SECONDS);
    RecordedRequest eventRequest = webhookServer.takeRequest(2, TimeUnit.SECONDS);

    assertThat(createRequest).isNotNull();
    assertThat(eventRequest).isNotNull();
    assertThat(eventRequest.getPath()).isEqualTo("/internal/events");
    assertThat(eventRequest.getHeader("X-Service-Token")).isEqualTo("test-service");

    JsonNode eventBody = objectMapper.readTree(eventRequest.getBody().readUtf8());
    assertThat(eventBody.get("event").asText()).isEqualTo("delivery.created");
    assertThat(eventBody.get("apiKey").asText()).isEqualTo("test-key");
    assertThat(eventBody.get("data").get("deliveryId").asText()).isEqualTo(deliveryId);
  }

  private static MockResponse webhookResponse(RecordedRequest request) {
    try {
      if ("GET".equals(request.getMethod())) {
        String path = request.getPath();
        if (path != null && (path.equals("/v1/webhooks") || path.startsWith("/v1/webhooks?"))) {
          Map<String, Object> payload = new java.util.HashMap<>();
          payload.put("items", List.of());
          payload.put("nextCursor", null);
          String responseBody = MAPPER.writeValueAsString(payload);
          return new MockResponse()
              .setResponseCode(200)
              .setHeader("Content-Type", "application/json")
              .setBody(responseBody);
        }
        if (path != null && path.startsWith("/v1/webhooks/")) {
          String id = path.substring("/v1/webhooks/".length());
          if (id.equals("nao-existe")) {
            return new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"NOT_FOUND\",\"message\":\"Webhook not found\"}");
          }
          Instant now = Instant.now();
          String responseBody = MAPPER.writeValueAsString(Map.of(
              "id", id,
              "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
              "events", List.of("invoice.paid"),
              "enabled", true,
              "createdAt", now.toString(),
              "updatedAt", now.toString()
          ));
          return new MockResponse()
              .setResponseCode(200)
              .setHeader("Content-Type", "application/json")
              .setBody(responseBody);
        }
      }

      if ("POST".equals(request.getMethod()) && "/internal/events".equals(request.getPath())) {
        return new MockResponse().setResponseCode(202);
      }

      String idempotencyKey = request.getHeader("Idempotency-Key");
      String id = idempotencyKey == null
          ? UUID.randomUUID().toString()
          : IDEMPOTENCY_IDS.computeIfAbsent(idempotencyKey, key -> UUID.randomUUID().toString());

      String rawBody = request.getBody().clone().readUtf8();
      JsonNode body = MAPPER.readTree(rawBody);
      String targetUrl = body.get("targetUrl").asText();
      List<String> events = new ArrayList<>();
      JsonNode eventsNode = body.get("events");
      if (eventsNode != null && eventsNode.isArray()) {
        eventsNode.forEach(node -> events.add(node.asText()));
      }

      Instant now = Instant.now();
      String responseBody = MAPPER.writeValueAsString(Map.of(
          "id", id,
          "targetUrl", targetUrl,
          "events", events,
          "enabled", true,
          "createdAt", now.toString(),
          "updatedAt", now.toString()
      ));

      return new MockResponse()
          .setResponseCode(201)
          .setHeader("Content-Type", "application/json")
          .setBody(responseBody);
    } catch (Exception e) {
      return new MockResponse().setResponseCode(500);
    }
  }
}
