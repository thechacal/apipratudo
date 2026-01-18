package com.apipratudo.quota.dto;

import com.apipratudo.quota.model.ApiKeyStatus;
import com.apipratudo.quota.model.Plan;
import java.time.Instant;

public record ApiKeyResponse(
    String id,
    String name,
    String owner,
    String ownerEmail,
    String orgName,
    Plan plan,
    ApiKeyLimits limits,
    Instant createdAt,
    ApiKeyStatus status
) {
}
