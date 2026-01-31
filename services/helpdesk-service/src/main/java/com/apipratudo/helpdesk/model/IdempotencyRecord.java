package com.apipratudo.helpdesk.model;

import java.time.Instant;

public record IdempotencyRecord(
    String key,
    int statusCode,
    String bodyJson,
    Instant createdAt,
    Instant expiresAt
) {
}
