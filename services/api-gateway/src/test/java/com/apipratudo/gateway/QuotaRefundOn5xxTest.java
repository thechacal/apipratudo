package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apipratudo.gateway.webhook.service.WebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuotaRefundOn5xxTest {

  private static MockWebServer quotaServer;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private WebhookService webhookService;

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
  }

  @AfterAll
  static void shutdownQuotaServer() throws IOException {
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
  }

  @BeforeEach
  void stubWebhookService() {
    when(webhookService.create(any(), anyString()))
        .thenThrow(new RuntimeException("boom"));
  }

  @Test
  void refundCalledOnServerError() throws Exception {
    quotaServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"allowed\":true,\"limit\":100,\"remaining\":99}"));
    quotaServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"refunded\":true}"));

    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "allow-key")
            .header("Idempotency-Key", "req-500")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().is5xxServerError());

    RecordedRequest consumeRequest = quotaServer.takeRequest(1, TimeUnit.SECONDS);
    RecordedRequest refundRequest = quotaServer.takeRequest(1, TimeUnit.SECONDS);

    assertThat(consumeRequest).isNotNull();
    assertThat(refundRequest).isNotNull();
    assertThat(consumeRequest.getPath()).isEqualTo("/v1/quota/consume");
    assertThat(refundRequest.getPath()).isEqualTo("/v1/quota/refund");
    assertThat(consumeRequest.getHeader("X-Internal-Token")).isEqualTo("test-internal");
    assertThat(refundRequest.getHeader("X-Internal-Token")).isEqualTo("test-internal");

    JsonNode consumeBody = objectMapper.readTree(consumeRequest.getBody().readUtf8());
    JsonNode refundBody = objectMapper.readTree(refundRequest.getBody().readUtf8());
    assertThat(consumeBody.get("requestId").asText()).isEqualTo("req-500");
    assertThat(refundBody.get("requestId").asText()).isEqualTo("req-500");
  }
}
