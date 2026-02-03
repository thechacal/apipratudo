package com.apipratudo.reconciliation.dto;

import java.time.Instant;

public record ImportResponse(
    String importId,
    String format,
    Instant createdAt,
    int totalTransactions,
    long totalCreditsCents,
    long totalDebitsCents
) {
}
