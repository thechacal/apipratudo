package com.apipratudo.quota.model;

import com.apipratudo.quota.dto.ApiKeyLimits;
import java.time.Instant;

public record ApiKey(
    String id,
    String apiKeyHash,
    String name,
    String owner,
    ApiKeyLimits limits,
    Instant createdAt,
    ApiKeyStatus status
) {
}
