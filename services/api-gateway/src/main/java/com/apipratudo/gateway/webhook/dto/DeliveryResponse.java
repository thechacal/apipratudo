package com.apipratudo.gateway.webhook.dto;

import java.time.Instant;

public record DeliveryResponse(
    String id,
    String webhookId,
    String eventType,
    String targetUrl,
    String status,
    int attempt,
    int responseCode,
    Instant createdAt
) {
}
