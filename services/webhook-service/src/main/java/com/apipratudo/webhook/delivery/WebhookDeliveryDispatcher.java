package com.apipratudo.webhook.delivery;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WebhookDeliveryDispatcher {

  private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryDispatcher.class);
  private static final HexFormat HEX = HexFormat.of();

  private final DeliveryOutboxRepository outboxRepository;
  private final DeliveryDispatchProperties properties;
  private final Clock clock;
  private final HttpClient httpClient;

  public WebhookDeliveryDispatcher(
      DeliveryOutboxRepository outboxRepository,
      DeliveryDispatchProperties properties,
      Clock clock
  ) {
    this.outboxRepository = outboxRepository;
    this.properties = properties;
    this.clock = clock;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
        .build();
  }

  @Scheduled(fixedDelayString = "${app.dispatcher.interval-ms:1000}")
  public void processOutbox() {
    Instant now = Instant.now(clock);
    for (OutboundDelivery delivery : outboxRepository.findDue(now, properties.getBatchSize())) {
      dispatch(delivery);
    }
  }

  private void dispatch(OutboundDelivery delivery) {
    int attempt = delivery.attemptCount() + 1;
    Instant now = Instant.now(clock);
    AttemptResult result = send(delivery);

    boolean success = result.statusCode != null && result.statusCode >= 200 && result.statusCode < 300;
    boolean shouldRetry = !success && result.retryable && attempt < properties.getMaxAttempts();
    OutboundDeliveryStatus status = success
        ? OutboundDeliveryStatus.DELIVERED
        : shouldRetry ? OutboundDeliveryStatus.PENDING : OutboundDeliveryStatus.FAILED_FINAL;

    Instant nextRetryAt = null;
    if (shouldRetry) {
      long delaySeconds = (long) Math.pow(2, attempt - 1);
      nextRetryAt = now.plusSeconds(delaySeconds);
    }

    OutboundDelivery updated = new OutboundDelivery(
        delivery.id(),
        delivery.webhookId(),
        delivery.apiKey(),
        delivery.deliveryId(),
        delivery.event(),
        delivery.targetUrl(),
        delivery.secret(),
        delivery.payloadJson(),
        status,
        attempt,
        nextRetryAt,
        result.statusCode,
        result.errorMessage,
        delivery.createdAt(),
        now
    );

    outboxRepository.save(updated);

    if (status == OutboundDeliveryStatus.DELIVERED) {
      log.info("Delivery delivered id={} webhookId={} statusCode={}", updated.id(), updated.webhookId(),
          result.statusCode);
    } else if (status == OutboundDeliveryStatus.PENDING && nextRetryAt != null) {
      log.warn("Delivery retry scheduled id={} webhookId={} attempt={} nextRetryAt={} statusCode={}",
          updated.id(), updated.webhookId(), attempt + 1, nextRetryAt, result.statusCode);
    } else {
      log.error("Delivery failed id={} webhookId={} statusCode={} error={}", updated.id(), updated.webhookId(),
          result.statusCode, result.errorMessage);
    }
  }

  private AttemptResult send(OutboundDelivery delivery) {
    try {
      byte[] payloadBytes = delivery.payloadJson().getBytes(StandardCharsets.UTF_8);
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(delivery.targetUrl()))
          .timeout(Duration.ofMillis(properties.getTimeoutMs()))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofByteArray(payloadBytes));

      if (StringUtils.hasText(delivery.event())) {
        builder.header("X-Apipratudo-Event", delivery.event());
      }
      if (StringUtils.hasText(delivery.deliveryId())) {
        builder.header("X-Apipratudo-Delivery-Id", delivery.deliveryId());
      }
      if (StringUtils.hasText(delivery.secret())) {
        String signature = "sha256=" + hmacSha256Hex(delivery.secret(), payloadBytes);
        builder.header("X-Apipratudo-Signature", signature);
      }

      HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
      int statusCode = response.statusCode();
      boolean retryable = statusCode >= 500;
      if (statusCode >= 400 && statusCode < 500) {
        retryable = false;
      }
      return new AttemptResult(statusCode, retryable, null);
    } catch (Exception ex) {
      String message = ex.getMessage() == null ? ex.getClass().getSimpleName()
          : ex.getClass().getSimpleName() + ": " + ex.getMessage();
      return new AttemptResult(null, true, message);
    }
  }

  private String hmacSha256Hex(String secret, byte[] payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    mac.init(keySpec);
    byte[] signature = mac.doFinal(payload);
    return HEX.formatHex(signature);
  }

  private static class AttemptResult {
    private final Integer statusCode;
    private final boolean retryable;
    private final String errorMessage;

    private AttemptResult(Integer statusCode, boolean retryable, String errorMessage) {
      this.statusCode = statusCode;
      this.retryable = retryable;
      this.errorMessage = errorMessage;
    }
  }
}
