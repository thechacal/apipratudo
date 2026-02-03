package com.apipratudo.reconciliation.dto;

public record MatchRunResponse(
    String importId,
    int matchedCount,
    int pendingTransactions,
    int pendingEvents
) {
}
