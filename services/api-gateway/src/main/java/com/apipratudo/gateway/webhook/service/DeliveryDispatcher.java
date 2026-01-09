package com.apipratudo.gateway.webhook.service;

import com.apipratudo.gateway.config.DeliveryProperties;
import com.apipratudo.gateway.webhook.model.Delivery;
import com.apipratudo.gateway.webhook.model.DeliveryAttempt;
import com.apipratudo.gateway.webhook.model.DeliveryStatus;
import com.apipratudo.gateway.webhook.repo.DeliveryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class DeliveryDispatcher {

  private static final Logger log = LoggerFactory.getLogger(DeliveryDispatcher.class);

  private final DeliveryRepository deliveryRepository;
  private final DeliveryRetryPolicy retryPolicy;
  private final DeliveryProperties properties;
  private final WebClient webClient;
  private final TaskScheduler taskScheduler;
  private final Clock clock;

  public DeliveryDispatcher(
      DeliveryRepository deliveryRepository,
      DeliveryRetryPolicy retryPolicy,
      DeliveryProperties properties,
      WebClient webClient,
      TaskScheduler taskScheduler,
      Clock clock
  ) {
    this.deliveryRepository = deliveryRepository;
    this.retryPolicy = retryPolicy;
    this.properties = properties;
    this.webClient = webClient;
    this.taskScheduler = taskScheduler;
    this.clock = clock;
  }

  public void dispatch(Delivery delivery, String traceId) {
    if (delivery == null) {
      return;
    }
    scheduleAttempt(delivery.id(), delivery.attempt(), traceId, Duration.ZERO);
  }

  private void scheduleAttempt(String deliveryId, int attempt, String traceId, Duration delay) {
    Instant scheduledAt = Instant.now(clock).plus(delay);
    taskScheduler.schedule(() -> executeAttempt(deliveryId, attempt, traceId), scheduledAt);
  }

  private void executeAttempt(String deliveryId, int attempt, String traceId) {
    Optional<Delivery> currentOpt = deliveryRepository.findById(deliveryId);
    if (currentOpt.isEmpty()) {
      log.warn("Delivery attempt skipped deliveryId={} reason=not_found traceId={}", deliveryId, safeTraceId(traceId));
      return;
    }

    Delivery current = currentOpt.get();
    if (current.status() == DeliveryStatus.DELIVERED || current.status() == DeliveryStatus.FAILED) {
      log.info("Delivery attempt skipped deliveryId={} status={} traceId={}", deliveryId, current.status(),
          safeTraceId(traceId));
      return;
    }

    log.info("Delivery attempt started deliveryId={} webhookId={} attempt={} traceId={}", deliveryId,
        current.webhookId(), attempt, safeTraceId(traceId));

    AttemptOutcome outcome;
    try {
      outcome = sendAttempt(current, attempt, traceId);
    } catch (Exception ex) {
      outcome = AttemptOutcome.failure(ex);
    }

    Integer statusCode = outcome.statusCode();
    String errorMessage = outcome.errorMessage();
    boolean success = statusCode != null && statusCode >= 200 && statusCode < 300;
    if (!success && errorMessage == null && statusCode != null) {
      errorMessage = "HTTP " + statusCode;
    }

    DeliveryAttempt attemptRecord = new DeliveryAttempt(
        attempt,
        statusCode,
        errorMessage,
        Instant.now(clock)
    );

    List<DeliveryAttempt> attempts = new ArrayList<>(current.attempts());
    attempts.add(attemptRecord);

    DeliveryStatus status;
    DeliveryRetryPolicy.RetryPlan retryPlan = null;
    if (success) {
      status = DeliveryStatus.DELIVERED;
    } else {
      retryPlan = retryPolicy.nextRetry(attempt, statusCode, outcome.retryAfter(), outcome.errorMessage() != null);
      status = retryPlan.retry() ? DeliveryStatus.PENDING : DeliveryStatus.FAILED;
    }

    int responseCode = statusCode == null ? 0 : statusCode;
    Delivery updated = new Delivery(
        current.id(),
        current.webhookId(),
        current.eventType(),
        current.targetUrl(),
        status,
        attempt,
        responseCode,
        current.createdAt(),
        attempts
    );

    deliveryRepository.save(updated);

    if (success) {
      log.info("Delivery delivered deliveryId={} webhookId={} attempt={} statusCode={} traceId={}", updated.id(),
          updated.webhookId(), attempt, responseCode, safeTraceId(traceId));
      return;
    }

    if (retryPlan != null && retryPlan.retry()) {
      long delayMs = retryPlan.delay().toMillis();
      log.warn("Delivery retry scheduled deliveryId={} webhookId={} attempt={} delayMs={} statusCode={} traceId={}",
          updated.id(), updated.webhookId(), attempt + 1, delayMs, responseCode, safeTraceId(traceId));
      scheduleAttempt(updated.id(), attempt + 1, traceId, retryPlan.delay());
      return;
    }

    log.error("Delivery failed deliveryId={} webhookId={} attempt={} statusCode={} error={} traceId={}", updated.id(),
        updated.webhookId(), attempt, responseCode, errorMessage, safeTraceId(traceId));
  }

  private AttemptOutcome sendAttempt(Delivery delivery, int attempt, String traceId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("deliveryId", delivery.id());
    payload.put("webhookId", delivery.webhookId());
    payload.put("eventType", delivery.eventType());
    payload.put("attempt", attempt);
    payload.put("createdAt", delivery.createdAt());

    WebClient.RequestBodySpec spec = webClient.post()
        .uri(delivery.targetUrl())
        .contentType(MediaType.APPLICATION_JSON);

    if (traceId != null && !traceId.isBlank()) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    Mono<AttemptOutcome> call = spec.bodyValue(payload)
        .exchangeToMono(response -> {
          String retryAfter = response.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER);
	  int statusCode = response.statusCode().value();
          return response.releaseBody().thenReturn(new AttemptOutcome(statusCode, retryAfter, null));
        })
        .timeout(Duration.ofMillis(properties.getTimeoutMs()));

    AttemptOutcome outcome = call.block();
    if (outcome == null) {
      throw new IllegalStateException("Delivery response missing");
    }
    return outcome;
  }

  private String safeTraceId(String traceId) {
    return traceId == null || traceId.isBlank() ? "-" : traceId;
  }

  private record AttemptOutcome(Integer statusCode, String retryAfter, String errorMessage) {
    static AttemptOutcome failure(Exception ex) {
      String message = ex.getMessage() == null ? ex.getClass().getSimpleName()
          : ex.getClass().getSimpleName() + ": " + ex.getMessage();
      return new AttemptOutcome(null, null, message);
    }
  }
}
