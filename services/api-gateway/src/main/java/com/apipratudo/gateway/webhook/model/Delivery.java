package com.apipratudo.gateway.webhook.model;

import java.time.Instant;

public record Delivery(
    String id,
    String webhookId,
    String eventType,
    String targetUrl,
    DeliveryStatus status,
    int attempt,
    int responseCode,
    Instant createdAt
) {
}
