package com.apipratudo.webhook.dto;

import java.time.Instant;
import java.util.List;

public record WebhookResponse(
    String id,
    String targetUrl,
    List<String> events,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}
