package com.apipratudo.quota.dto;

import com.apipratudo.quota.model.Plan;
import java.time.Instant;

public record ApiKeyCreateResponse(
    String id,
    String apiKey,
    String name,
    String owner,
    String ownerEmail,
    String orgName,
    Plan plan,
    ApiKeyLimits limits,
    Instant createdAt
) {
}
