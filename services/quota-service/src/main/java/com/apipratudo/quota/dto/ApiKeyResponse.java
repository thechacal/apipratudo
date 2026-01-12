package com.apipratudo.quota.dto;

import java.time.Instant;

public record ApiKeyResponse(
    String id,
    String name,
    String owner,
    ApiKeyLimits limits,
    Instant createdAt
) {
}
