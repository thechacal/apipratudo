package com.apipratudo.quota.dto;

import java.time.Instant;

public record QuotaRefundResponse(
    boolean refunded,
    QuotaReason reason,
    Long limit,
    Long remaining,
    Instant resetAt
) {
}
