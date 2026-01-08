package com.apipratudo.gateway.webhook.storage;

import java.time.Instant;

public record WebhookRecord(
    String id,
    String targetUrl,
    String eventType,
    String status,
    Instant createdAt
) {
}
