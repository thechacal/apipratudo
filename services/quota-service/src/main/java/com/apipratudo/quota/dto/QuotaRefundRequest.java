package com.apipratudo.quota.dto;

import jakarta.validation.constraints.NotBlank;

public record QuotaRefundRequest(
    @NotBlank String apiKey,
    @NotBlank String requestId
) {
}
