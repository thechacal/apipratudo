package com.apipratudo.quota.dto;

import com.apipratudo.quota.model.Plan;
import java.time.Instant;

public record CreateFreeKeyResponse(
    String id,
    String apiKey,
    String ownerEmail,
    String orgName,
    Plan plan,
    ApiKeyLimits limits,
    Instant createdAt
) {
}
