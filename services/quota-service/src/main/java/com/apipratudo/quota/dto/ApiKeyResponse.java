package com.apipratudo.quota.dto;

import java.time.Instant;

import com.apipratudo.quota.model.ApiKeyStatus;

public record ApiKeyResponse(
    String id,
    String name,
    String owner,
    ApiKeyLimits limits,
    Instant createdAt,
    ApiKeyStatus status
) {
}
