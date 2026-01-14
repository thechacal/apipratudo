package com.apipratudo.webhook.delivery;

import java.time.Instant;

public record OutboundDelivery(
    String id,
    String webhookId,
    String apiKey,
    String deliveryId,
    String event,
    String targetUrl,
    String secret,
    String payloadJson,
    OutboundDeliveryStatus status,
    int attemptCount,
    Instant nextRetryAt,
    Integer lastStatusCode,
    String lastError,
    Instant createdAt,
    Instant updatedAt
) {
}
