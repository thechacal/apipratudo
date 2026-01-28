package com.apipratudo.scheduling.client;

import java.time.Instant;

public record WebhookEventData(
    String deliveryId,
    String status,
    Instant createdAt
) {
}
