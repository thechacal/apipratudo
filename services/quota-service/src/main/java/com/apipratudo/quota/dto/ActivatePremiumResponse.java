package com.apipratudo.quota.dto;

import com.apipratudo.quota.model.Plan;

public record ActivatePremiumResponse(
    String id,
    Plan plan,
    ApiKeyLimits limits
) {
}
