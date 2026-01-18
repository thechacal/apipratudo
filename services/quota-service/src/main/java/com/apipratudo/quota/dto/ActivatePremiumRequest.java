package com.apipratudo.quota.dto;

import com.apipratudo.quota.model.Plan;
import jakarta.validation.Valid;

public record ActivatePremiumRequest(
    String apiKey,
    String apiKeyHash,
    Plan plan,
    @Valid ApiKeyLimits limits
) {
}
