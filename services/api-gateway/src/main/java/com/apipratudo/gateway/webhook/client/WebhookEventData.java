package com.apipratudo.gateway.webhook.client;

import java.time.Instant;

public record WebhookEventData(
    String deliveryId,
    String status,
    Instant createdAt
) {
}
