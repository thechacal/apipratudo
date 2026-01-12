package com.apipratudo.quota.model;

import java.time.Instant;

public record QuotaWindowStatus(
    long limit,
    long used,
    long remaining,
    Instant resetAt
) {
}
