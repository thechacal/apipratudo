package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuotaEnforcementFilterTest {

  private static MockWebServer quotaServer;
  private static MockWebServer webhookServer;
  private static MockWebServer billingSaasServer;
  private static MockWebServer helpdeskServer;

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
      webhookServer.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
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

    if (billingSaasServer == null) {
      billingSaasServer = new MockWebServer();
      try {
        billingSaasServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start billing-saas mock server", e);
      }
    }
    registry.add("billing-saas.base-url", () -> billingSaasServer.url("/").toString());
    registry.add("billing-saas.timeout-ms", () -> 2000);
    registry.add("billing-saas.service-token", () -> "test-billing-saas");

    if (helpdeskServer == null) {
      helpdeskServer = new MockWebServer();
      try {
        helpdeskServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start helpdesk mock server", e);
      }
    }
    registry.add("helpdesk.base-url", () -> helpdeskServer.url("/").toString());
    registry.add("helpdesk.timeout-ms", () -> 2000);
    registry.add("helpdesk.service-token", () -> "test-helpdesk");
  }

  @AfterAll
  static void shutdownQuotaServer() throws IOException {
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
    if (webhookServer != null) {
      webhookServer.shutdown();
    }
    if (billingSaasServer != null) {
      billingSaasServer.shutdown();
    }
    if (helpdeskServer != null) {
      helpdeskServer.shutdown();
    }
  }

  @AfterEach
  void drainQuotaRequests() throws InterruptedException {
    if (quotaServer == null) {
      return;
    }
    while (quotaServer.takeRequest(50, TimeUnit.MILLISECONDS) != null) {
      // drain any leftover requests to keep tests isolated
    }
    if (webhookServer == null) {
      return;
    }
    while (webhookServer.takeRequest(50, TimeUnit.MILLISECONDS) != null) {
      // drain webhook requests to keep tests isolated
    }
    if (billingSaasServer == null) {
      return;
    }
    while (billingSaasServer.takeRequest(50, TimeUnit.MILLISECONDS) != null) {
      // drain billing-saas requests to keep tests isolated
    }
    if (helpdeskServer == null) {
      return;
    }
    while (helpdeskServer.takeRequest(50, TimeUnit.MILLISECONDS) != null) {
      // drain helpdesk requests to keep tests isolated
    }
  }

  @Test
  void missingApiKeyReturns401() throws Exception {
    mockMvc.perform(get("/v1/webhooks"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  @Test
  void quotaExceededReturns402() throws Exception {
    quotaServer.enqueue(new MockResponse()
        .setResponseCode(402)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"allowed\":false,\"reason\":\"QUOTA_EXCEEDED\",\"error\":\"QUOTA_EXCEEDED\",\"plan\":\"FREE\"}"));

    mockMvc.perform(get("/v1/webhooks")
            .header("X-Api-Key", "rate-key"))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.error").value("QUOTA_EXCEEDED"))
        .andExpect(jsonPath("$.message").value("Cota esgotada. Recarregue creditos para continuar."))
        .andExpect(jsonPath("$.upgrade.endpoint").value("/v1/keys/upgrade"))
        .andExpect(jsonPath("$.upgrade.docs").value("/docs"));
  }

  @Test
  void quotaAuthMisconfiguredReturns500() throws Exception {
    quotaServer.enqueue(new MockResponse()
        .setResponseCode(401)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid X-Internal-Token\"}"));

    mockMvc.perform(get("/v1/webhooks")
            .header("X-Api-Key", "any-key"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("QUOTA_AUTH_MISCONFIGURED"))
        .andExpect(jsonPath("$.message").value("Quota auth misconfigured"));
  }

  @Test
  void allowedRequestCallsConsumeAndNoRefund() throws Exception {
    quotaServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"allowed\":true,\"limit\":100,\"remaining\":99}"));

    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "allow-key")
            .header("Idempotency-Key", "idem-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());

    RecordedRequest consumeRequest = quotaServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(consumeRequest).isNotNull();
    assertThat(consumeRequest.getPath()).isEqualTo("/v1/quota/consume");
    assertThat(consumeRequest.getHeader("X-Internal-Token")).isEqualTo("test-internal");

    JsonNode payload = objectMapper.readTree(consumeRequest.getBody().readUtf8());
    assertThat(payload.get("requestId").asText()).isEqualTo("idem-123");

    RecordedRequest refundRequest = quotaServer.takeRequest(200, TimeUnit.MILLISECONDS);
    assertThat(refundRequest).isNull();
  }

  @Test
  void pixWebhookBypassesQuota() throws Exception {
    billingSaasServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"ok\":true}"));

    mockMvc.perform(post("/v1/pix/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Webhook-Secret", "test-secret")
            .content("{\"provider\":\"FAKE\",\"providerChargeId\":\"fake-1\",\"event\":\"PAID\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    RecordedRequest quotaRequest = quotaServer.takeRequest(200, TimeUnit.MILLISECONDS);
    assertThat(quotaRequest).isNull();

    RecordedRequest webhookRequest = billingSaasServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(webhookRequest).isNotNull();
    assertThat(webhookRequest.getPath()).isEqualTo("/internal/pix/webhook");
    assertThat(webhookRequest.getHeader("X-Service-Token")).isEqualTo("test-billing-saas");
    assertThat(webhookRequest.getHeader("X-Webhook-Secret")).isEqualTo("test-secret");
  }

  @Test
  void whatsappWebhookBypassesQuota() throws Exception {
    helpdeskServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"ok\":true}"));

    byte[] payload = "{\"entry\":[]}".getBytes();
    mockMvc.perform(post("/v1/webhook/whatsapp")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", "sha256=fake")
            .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    RecordedRequest quotaRequest = quotaServer.takeRequest(200, TimeUnit.MILLISECONDS);
    assertThat(quotaRequest).isNull();

    RecordedRequest helpdeskRequest = helpdeskServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(helpdeskRequest).isNotNull();
    assertThat(helpdeskRequest.getPath()).isEqualTo("/internal/helpdesk/webhook/whatsapp");
    assertThat(helpdeskRequest.getHeader("X-Service-Token")).isEqualTo("test-helpdesk");
    assertThat(helpdeskRequest.getHeader("X-Hub-Signature-256")).isEqualTo("sha256=fake");
    assertThat(helpdeskRequest.getBody().readByteArray()).isEqualTo(payload);
  }

  @Test
  void pixWebhookGetStillRequiresApiKey() throws Exception {
    mockMvc.perform(get("/v1/pix/webhook"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  @Test
  void pagbankConnectBypassesQuotaButRequiresApiKey() throws Exception {
    billingSaasServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"connected\":true,\"environment\":\"SANDBOX\"}"));

    mockMvc.perform(post("/v1/provedores/pagbank/conectar")
            .header("X-Api-Key", "api-key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"token-123\",\"environment\":\"SANDBOX\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));

    RecordedRequest quotaRequest = quotaServer.takeRequest(200, TimeUnit.MILLISECONDS);
    assertThat(quotaRequest).isNull();

    RecordedRequest saasRequest = billingSaasServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(saasRequest).isNotNull();
    assertThat(saasRequest.getPath()).isEqualTo("/internal/providers/pagbank/connect");
    assertThat(saasRequest.getHeader("X-Tenant-Id")).isNotBlank();
    assertThat(saasRequest.getHeader("X-Api-Key")).isNull();
  }
}
