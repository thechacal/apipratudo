package com.apipratudo.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apipratudo.billing.model.BillingCharge;
import com.apipratudo.billing.repository.BillingChargeRepository;
import com.apipratudo.billing.service.HashingUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingControllerTest {

  private static MockWebServer pagbankServer;
  private static MockWebServer quotaServer;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private BillingChargeRepository repository;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    if (pagbankServer == null) {
      pagbankServer = new MockWebServer();
      try {
        pagbankServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start PagBank mock", e);
      }
    }
    if (quotaServer == null) {
      quotaServer = new MockWebServer();
      try {
        quotaServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start quota mock", e);
      }
    }
    registry.add("app.pagbank.base-url", () -> pagbankServer.url("/").toString());
    registry.add("app.quota.base-url", () -> quotaServer.url("/").toString());
    registry.add("app.quota.internal-token", () -> "test-internal");
    registry.add("app.pagbank.token", () -> "test-pagbank-token");
    registry.add("app.pagbank.webhook-token", () -> "test-webhook-token");
    registry.add("app.security.service-token", () -> "test-service-token");
    registry.add("app.webhook.secret", () -> "test-webhook-secret");
  }

  @AfterAll
  static void shutdownServers() throws IOException {
    if (pagbankServer != null) {
      pagbankServer.shutdown();
    }
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
  }

  @Test
  void createChargeReturnsPixData() throws Exception {
    String base64Url = pagbankServer.url("/qrcode").toString();
    pagbankServer.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) {
        if ("POST".equals(request.getMethod()) && "/orders".equals(request.getPath())) {
          String body = "{\"id\":\"order-123\",\"reference_id\":\"ref-1\",\"status\":\"CREATED\"," +
              "\"created_at\":\"2024-01-01T10:00:00-03:00\",\"qr_codes\":[{" +
              "\"id\":\"qr-1\",\"text\":\"000201\",\"expiration_date\":\"2024-01-01T10:10:00-03:00\"," +
              "\"links\":[{\"rel\":\"QRCODE.BASE64\",\"href\":\"" + base64Url + "\"}]}]}";
          return new MockResponse()
              .setResponseCode(200)
              .setHeader("Content-Type", "application/json")
              .setBody(body);
        }
        if ("GET".equals(request.getMethod()) && "/qrcode".equals(request.getPath())) {
          return new MockResponse().setResponseCode(200).setBody("BASE64DATA");
        }
        return new MockResponse().setResponseCode(404);
      }
    });

    mockMvc.perform(post("/v1/billing/pix/charges")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", "test-service-token")
            .content("{\"apiKey\":\"key-123\",\"plan\":\"PREMIUM\",\"amountCents\":4900}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.chargeId").value("order-123"))
        .andExpect(jsonPath("$.status").value("CREATED"))
        .andExpect(jsonPath("$.pixCopyPaste").value("000201"))
        .andExpect(jsonPath("$.qrCodeBase64").value("BASE64DATA"));

    RecordedRequest orderRequest = pagbankServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(orderRequest).isNotNull();
    assertThat(orderRequest.getPath()).isEqualTo("/orders");
    assertThat(orderRequest.getHeader("Authorization")).startsWith("Bearer ");

    RecordedRequest qrRequest = pagbankServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(qrRequest).isNotNull();
    assertThat(qrRequest.getPath()).isEqualTo("/qrcode");
  }

  @Test
  void webhookActivatesPremium() throws Exception {
    String apiKeyHash = HashingUtils.sha256Hex("key-456");
    BillingCharge charge = new BillingCharge(
        "order-456",
        "ref-456",
        apiKeyHash,
        apiKeyHash.substring(0, 8),
        "PREMIUM",
        4900,
        "Plano PREMIUM",
        null,
        "CREATED",
        Boolean.FALSE,
        Instant.now(),
        Instant.now(),
        Instant.now(),
        "pix-copy",
        "qr",
        null,
        Boolean.FALSE
    );
    repository.save(charge);

    quotaServer.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) {
        if ("POST".equals(request.getMethod()) && "/v1/subscriptions/activate-premium".equals(request.getPath())) {
          return new MockResponse().setResponseCode(200).setBody("{}");
        }
        return new MockResponse().setResponseCode(404);
      }
    });

    String payload = "{\"id\":\"order-456\",\"reference_id\":\"ref-456\",\"status\":\"PAID\"," +
        "\"charges\":[{\"status\":\"PAID\"}]}";
    String signature = signPayload("test-webhook-token", payload);

    mockMvc.perform(post("/v1/billing/pix/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Webhook-Secret", "test-webhook-secret")
            .header("x-authenticity-token", signature)
            .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.mode").value("json_processed"));

    RecordedRequest activation = quotaServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(activation).isNotNull();
    assertThat(activation.getHeader("X-Internal-Token")).isEqualTo("test-internal");
    assertThat(activation.getBody().readUtf8()).contains(apiKeyHash);
  }

  @Test
  void statusChecksPagBank() throws Exception {
    String apiKeyHash = HashingUtils.sha256Hex("key-789");
    BillingCharge charge = new BillingCharge(
        "order-789",
        "ref-789",
        apiKeyHash,
        apiKeyHash.substring(0, 8),
        "PREMIUM",
        4900,
        "Plano PREMIUM",
        null,
        "CREATED",
        Boolean.FALSE,
        Instant.now(),
        Instant.now(),
        Instant.now(),
        "pix-copy",
        "qr",
        null,
        Boolean.FALSE
    );
    repository.save(charge);

    pagbankServer.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) {
        if ("GET".equals(request.getMethod()) && "/orders/order-789".equals(request.getPath())) {
          String body = "{\"id\":\"order-789\",\"status\":\"PAID\",\"charges\":[{\"status\":\"PAID\"}]}";
          return new MockResponse()
              .setResponseCode(200)
              .setHeader("Content-Type", "application/json")
              .setBody(body);
        }
        return new MockResponse().setResponseCode(404);
      }
    });

    quotaServer.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) {
        if ("POST".equals(request.getMethod()) && "/v1/subscriptions/activate-premium".equals(request.getPath())) {
          return new MockResponse().setResponseCode(200).setBody("{}");
        }
        return new MockResponse().setResponseCode(404);
      }
    });

    mockMvc.perform(get("/v1/billing/pix/charges/order-789")
            .header("X-Service-Token", "test-service-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chargeId").value("order-789"))
        .andExpect(jsonPath("$.paid").value(true))
        .andExpect(jsonPath("$.premiumActivated").value(true));

    RecordedRequest pagbankRequest = pagbankServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(pagbankRequest).isNotNull();
    assertThat(pagbankRequest.getPath()).isEqualTo("/orders/order-789");

    RecordedRequest activation = quotaServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(activation).isNotNull();
    assertThat(activation.getBody().readUtf8()).contains(apiKeyHash);
  }

  private String signPayload(String token, String payload) throws Exception {
    byte[] prefix = (token + "-").getBytes(StandardCharsets.UTF_8);
    byte[] body = payload.getBytes(StandardCharsets.UTF_8);
    byte[] combined = new byte[prefix.length + body.length];
    System.arraycopy(prefix, 0, combined, 0, prefix.length);
    System.arraycopy(body, 0, combined, prefix.length, body.length);
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(combined);
    StringBuilder sb = new StringBuilder(hash.length * 2);
    for (byte b : hash) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
