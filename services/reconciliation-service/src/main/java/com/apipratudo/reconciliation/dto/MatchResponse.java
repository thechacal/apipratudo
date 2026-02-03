package com.apipratudo.reconciliation.dto;

public record MatchResponse(
    String matchId,
    String importId,
    String txId,
    String eventId,
    String ruleApplied,
    double confidence,
    String matchedAt
) {
}
