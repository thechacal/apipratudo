package com.apipratudo.gateway.webhook.dto;

import java.time.Instant;

public record DeliveryResponse(
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
