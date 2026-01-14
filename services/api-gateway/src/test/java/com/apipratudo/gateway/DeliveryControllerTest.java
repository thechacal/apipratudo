package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
class DeliveryControllerTest {

  private static MockWebServer quotaServer;
  private static MockWebServer webhookServer;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @DynamicPropertySource
  static void registerQuotaProperties(DynamicPropertyRegistry registry) {
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
    registry.add("quota.base-url", () -> quotaServer.url("/").toString());
    registry.add("quota.timeout-ms", () -> 2000);
    registry.add("quota.internal-token", () -> "test-internal");

    if (webhookServer == null) {
      webhookServer = new MockWebServer();
      webhookServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          if ("POST".equals(request.getMethod()) && "/internal/events".equals(request.getPath())) {
            return new MockResponse().setResponseCode(202);
          }
          if ("POST".equals(request.getMethod()) && "/v1/webhooks".equals(request.getPath())) {
            return new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"wh-test\",\"targetUrl\":\"https://cliente.exemplo.com/webhooks/apipratudo\",\"events\":[\"invoice.paid\"],\"enabled\":true,\"createdAt\":\"2024-01-01T00:00:00Z\",\"updatedAt\":\"2024-01-01T00:00:00Z\"}");
          }
          return new MockResponse().setResponseCode(404);
        }
      });
      try {
        webhookServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start webhook mock server", e);
      }
    }
    registry.add("webhook.base-url", () -> webhookServer.url("/").toString());
    registry.add("webhook.timeout-ms", () -> 2000);
    registry.add("webhook.service-token", () -> "test-service");
  }

  @AfterAll
  static void shutdownQuotaServer() throws IOException {
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
    if (webhookServer != null) {
      webhookServer.shutdown();
    }
  }

  @Test
  void listAndRetryDeliveries() throws Exception {
    String webhookId = createWebhook();
    String deliveryId = createDeliveryForWebhook(webhookId);

    MvcResult listResult = mockMvc.perform(get("/v1/deliveries")
            .param("webhookId", webhookId)
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode listJson = objectMapper.readTree(listResult.getResponse().getContentAsString());
    boolean contains = false;
    for (JsonNode item : listJson.get("items")) {
      if (webhookId.equals(item.get("webhookId").asText())) {
        contains = true;
        break;
      }
    }
    assertThat(contains).isTrue();

    mockMvc.perform(get("/v1/deliveries/{id}", deliveryId)
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isOk());

    MvcResult retryResult = mockMvc.perform(post("/v1/deliveries/{id}/retry", deliveryId)
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode retryJson = objectMapper.readTree(retryResult.getResponse().getContentAsString());
    assertThat(retryJson.get("id").asText()).isNotEqualTo(deliveryId);
    assertThat(retryJson.get("attempt").asInt()).isEqualTo(2);
  }

  @Test
  void getDeliveryNotFound() throws Exception {
    mockMvc.perform(get("/v1/deliveries/nao-existe")
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isNotFound());
  }

  private String createWebhook() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    MvcResult result = mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("id").asText();
  }

  private String createDeliveryForWebhook(String webhookId) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/webhooks/{id}/test", webhookId)
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("deliveryId").asText();
  }
}
