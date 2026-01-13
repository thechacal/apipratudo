package com.apipratudo.quota.model;

import java.time.Instant;

public record QuotaWindow(
    QuotaWindowType type,
    Instant windowStart,
    Instant resetAt
) {
}
