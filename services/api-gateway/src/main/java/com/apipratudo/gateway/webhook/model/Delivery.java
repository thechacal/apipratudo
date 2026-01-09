package com.apipratudo.gateway.webhook.model;

import java.time.Instant;
import java.util.List;

public record Delivery(
    String id,
    String webhookId,
    String eventType,
    String targetUrl,
    DeliveryStatus status,
    int attempt,
    int responseCode,
    Instant createdAt,
    List<DeliveryAttempt> attempts
) {
  public Delivery {
    attempts = attempts == null ? List.of() : List.copyOf(attempts);
  }
}
