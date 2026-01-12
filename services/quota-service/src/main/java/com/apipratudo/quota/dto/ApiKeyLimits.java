package com.apipratudo.quota.dto;

import jakarta.validation.constraints.Min;

public record ApiKeyLimits(
    @Min(1) int requestsPerMinute,
    @Min(1) int requestsPerDay
) {
}
