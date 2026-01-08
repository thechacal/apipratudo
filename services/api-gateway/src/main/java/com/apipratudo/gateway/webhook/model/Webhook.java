package com.apipratudo.gateway.webhook.model;

import java.time.Instant;

public record Webhook(
    String id,
    String targetUrl,
    String eventType,
    WebhookStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
