package com.apipratudo.quota.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record QuotaConsumeRequest(
    @NotBlank String apiKey,
    @NotBlank String requestId,
    @NotBlank String route,
    @Min(1) Integer cost
) {
}
