package com.apipratudo.webhook;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apipratudo.webhook.delivery.DeliveryOutboxRepository;
import com.apipratudo.webhook.delivery.OutboundDelivery;
import com.apipratudo.webhook.delivery.OutboundDeliveryStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookDeliveryIntegrationTest {

  private static final HexFormat HEX = HexFormat.of();

  private WireMockServer wireMock;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DeliveryOutboxRepository outboxRepository;

  @BeforeEach
  void setup() {
    wireMock = new WireMockServer(0);
    wireMock.start();
    outboxRepository.deleteAll();
  }

  @AfterEach
  void cleanup() {
    if (wireMock != null) {
      wireMock.stop();
    }
    outboxRepository.deleteAll();
  }

  @Test
  void deliverySuccessSignsPayload() throws Exception {
    wireMock.stubFor(WireMock.post("/hooks").willReturn(aResponse().withStatus(200)));

    String apiKey = "wh-test";
    String secret = "s3cr3t";
    createWebhook(apiKey, secret);

    String deliveryId = "del-100";
    publishEvent(apiKey, deliveryId, "PENDING");

    awaitRequests(1, Duration.ofSeconds(3));

    List<ServeEvent> events = wireMock.getAllServeEvents();
    assertThat(events).hasSize(1);

    ServeEvent received = events.get(0);
    byte[] body = received.getRequest().getBody();
    String signatureHeader = received.getRequest().getHeader("X-Apipratudo-Signature");
    String expectedSignature = "sha256=" + hmacSha256Hex(secret, body);

    assertThat(received.getRequest().getHeader("X-Apipratudo-Event")).isEqualTo("delivery.created");
    assertThat(received.getRequest().getHeader("X-Apipratudo-Delivery-Id")).isEqualTo(deliveryId);
    assertThat(signatureHeader).isEqualTo(expectedSignature);

    JsonNode payload = objectMapper.readTree(body);
    assertThat(payload.get("event").asText()).isEqualTo("delivery.created");
    assertThat(payload.get("apiKey").asText()).isEqualTo(apiKey);
    assertThat(payload.get("data").get("deliveryId").asText()).isEqualTo(deliveryId);
  }

  @Test
  void deliveryRetriesAfterServerError() throws Exception {
    wireMock.stubFor(WireMock.post("/hooks")
        .inScenario("retry")
        .whenScenarioStateIs("Started")
        .willReturn(aResponse().withStatus(500))
        .willSetStateTo("second"));
    wireMock.stubFor(WireMock.post("/hooks")
        .inScenario("retry")
        .whenScenarioStateIs("second")
        .willReturn(aResponse().withStatus(200)));

    String apiKey = "wh-test";
    createWebhook(apiKey, null);

    String deliveryId = "del-200";
    publishEvent(apiKey, deliveryId, "PENDING");

    awaitRequests(2, Duration.ofSeconds(6));
    wireMock.verify(2, postRequestedFor(urlEqualTo("/hooks")));

    List<ServeEvent> events = wireMock.getAllServeEvents();
    assertThat(events).hasSizeGreaterThanOrEqualTo(2);
    events.sort(java.util.Comparator.comparing(event -> event.getRequest().getLoggedDate()));
    long first = events.get(0).getRequest().getLoggedDate().getTime();
    long second = events.get(1).getRequest().getLoggedDate().getTime();
    assertThat(second - first).isGreaterThanOrEqualTo(900L);

    OutboundDelivery stored = awaitDelivery(deliveryId, Duration.ofSeconds(3));
    assertThat(stored).isNotNull();
    assertThat(stored.status()).isEqualTo(OutboundDeliveryStatus.DELIVERED);
    assertThat(stored.attemptCount()).isEqualTo(2);
  }

  private void createWebhook(String apiKey, String secret) throws Exception {
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("targetUrl", wireMock.baseUrl() + "/hooks");
    body.put("events", List.of("delivery.created"));
    if (secret != null) {
      body.put("secret", secret);
    }
    mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());
  }

  private void publishEvent(String apiKey, String deliveryId, String status) throws Exception {
    Map<String, Object> body = Map.of(
        "event", "delivery.created",
        "apiKey", apiKey,
        "data", Map.of(
            "deliveryId", deliveryId,
            "status", status,
            "createdAt", Instant.now().toString()
        ),
        "occurredAt", Instant.now().toString()
    );

    mockMvc.perform(post("/internal/events")
            .header("X-Service-Token", "test-service")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isAccepted());
  }

  private void awaitRequests(int expected, Duration timeout) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    while (System.currentTimeMillis() < deadline) {
      if (wireMock.getAllServeEvents().size() >= expected) {
        return;
      }
      Thread.sleep(100);
    }
    assertThat(wireMock.getAllServeEvents().size()).isGreaterThanOrEqualTo(expected);
  }

  private OutboundDelivery awaitDelivery(String deliveryId, Duration timeout) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    while (System.currentTimeMillis() < deadline) {
      for (OutboundDelivery delivery : outboxRepository.findAll()) {
        if (deliveryId.equals(delivery.deliveryId())) {
          return delivery;
        }
      }
      Thread.sleep(100);
    }
    return null;
  }

  private String hmacSha256Hex(String secret, byte[] payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    mac.init(keySpec);
    return HEX.formatHex(mac.doFinal(payload));
  }
}
