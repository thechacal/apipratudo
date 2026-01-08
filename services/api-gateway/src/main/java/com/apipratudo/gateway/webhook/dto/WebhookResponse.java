package com.apipratudo.gateway.webhook.dto;

import java.time.Instant;

public record WebhookResponse(
    String id,
    String targetUrl,
    String eventType,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
