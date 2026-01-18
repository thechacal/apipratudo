package com.apipratudo.quota.dto;

import com.apipratudo.quota.model.Plan;

public record AddCreditsResponse(
    String apiKeyId,
    long creditsAdded,
    long creditsRemaining,
    Plan plan
) {
}
