package com.apipratudo.webhook.dto;

import java.time.Instant;

public record WebhookEventData(
    String deliveryId,
    String status,
    Instant createdAt
) {
}
