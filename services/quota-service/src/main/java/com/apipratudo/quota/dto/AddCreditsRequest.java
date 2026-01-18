package com.apipratudo.quota.dto;

import jakarta.validation.constraints.Min;

public record AddCreditsRequest(
    String apiKey,
    String apiKeyHash,
    @Min(1) long credits
) {
}
