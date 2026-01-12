package com.apipratudo.quota.model;

import com.apipratudo.quota.dto.QuotaReason;
import java.time.Instant;

public record QuotaDecision(
    boolean allowed,
    QuotaReason reason,
    long limit,
    long remaining,
    Instant resetAt
) {
}
