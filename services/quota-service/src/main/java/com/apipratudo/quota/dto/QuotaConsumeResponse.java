package com.apipratudo.quota.dto;

import java.time.Instant;

public record QuotaConsumeResponse(
    boolean allowed,
    QuotaReason reason,
    Long limit,
    Long remaining,
    Instant resetAt
) {
}
