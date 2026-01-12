package com.apipratudo.gateway.quota;

import java.time.Instant;

public record QuotaConsumeResponse(
    boolean allowed,
    String reason,
    Long limit,
    Long remaining,
    Instant resetAt
) {
}
