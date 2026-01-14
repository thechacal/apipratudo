package com.apipratudo.webhook.model;

import java.time.Instant;
import java.util.List;

public record Webhook(
    String id,
    String apiKey,
    String targetUrl,
    List<String> events,
    String secret,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt,
    String idempotencyKey
) {
}
