package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuotaIdempotencyRulesTest {

  private static final int LIMIT = 5;
  private static MockWebServer quotaServer;
  private static MockWebServer webhookServer;
  private static final AtomicInteger uniqueCounter = new AtomicInteger();
  private static final Set<String> seenRequestIds = ConcurrentHashMap.newKeySet();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @DynamicPropertySource
  static void registerQuotaProperties(DynamicPropertyRegistry registry) {
    if (quotaServer == null) {
      quotaServer = new MockWebServer();
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
          if ("GET".equals(request.getMethod()) && request.getPath() != null
              && request.getPath().startsWith("/v1/webhooks")) {
            return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"items\":[],\"nextCursor\":null}");
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

  @BeforeEach
  void setupDispatcher() {
    uniqueCounter.set(0);
    seenRequestIds.clear();
    quotaServer.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) {
        if (!"/v1/quota/consume".equals(request.getPath())) {
          return new MockResponse().setResponseCode(404);
        }
        try {
          JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
          String requestId = body.get("requestId").asText();
          boolean first = seenRequestIds.add(requestId);
          if (first && uniqueCounter.incrementAndGet() > LIMIT) {
            return new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"allowed\":false,\"reason\":\"RATE_LIMITED\"}");
          }
          return new MockResponse()
              .setResponseCode(200)
              .setHeader("Content-Type", "application/json")
              .setBody("{\"allowed\":true,\"limit\":5,\"remaining\":4}");
        } catch (Exception ex) {
          return new MockResponse().setResponseCode(500);
        }
      }
    });
  }

  @AfterEach
  void resetDispatcher() {
    quotaServer.setDispatcher(new QueueDispatcher());
    uniqueCounter.set(0);
    seenRequestIds.clear();
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
  void getIgnoresIdempotencyKeyForQuota() throws Exception {
    for (int i = 0; i < LIMIT; i++) {
      mockMvc.perform(get("/v1/webhooks")
              .header("X-Api-Key", "test-key")
              .header("Idempotency-Key", "same-key"))
          .andExpect(status().isOk());
    }

    mockMvc.perform(get("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .header("Idempotency-Key", "same-key"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void postUsesIdempotencyKeyForQuota() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    String idempotencyKey = "idem-" + UUID.randomUUID();

    mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());

    assertThat(uniqueCounter.get()).isEqualTo(1);
  }
}
