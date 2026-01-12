package com.apipratudo.quota.dto;

import java.time.Instant;

public record ApiKeyCreateResponse(
    String id,
    String apiKey,
    String name,
    String owner,
    ApiKeyLimits limits,
    Instant createdAt
) {
}
