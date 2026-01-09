package com.apipratudo.gateway.webhook.dto;

import com.apipratudo.gateway.webhook.model.WebhookStatus;
import java.time.Instant;

public record WebhookResponse(
    String id,
    String targetUrl,
    String eventType,
    WebhookStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
