package com.apipratudo.quota.model;

import com.apipratudo.quota.dto.QuotaReason;
import java.time.Instant;

public record QuotaRefundDecision(
    boolean refunded,
    QuotaReason reason,
    Long limit,
    Long remaining,
    Instant resetAt
) {
}
