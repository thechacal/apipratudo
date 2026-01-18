package com.apipratudo.quota.dto;

import com.apipratudo.quota.model.ApiKeyCredits;
import com.apipratudo.quota.model.Plan;

public record QuotaStatusResponse(
    String apiKeyId,
    Plan plan,
    ApiKeyLimits limits,
    QuotaUsage usage,
    ApiKeyCredits credits
) {
}
