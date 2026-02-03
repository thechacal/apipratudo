package com.apipratudo.reconciliation.model;

import java.time.Instant;

public record MatchResult(
    String id,
    String tenantId,
    String importId,
    String txId,
    String eventId,
    Instant matchedAt,
    String ruleApplied,
    double confidence
) {
}
