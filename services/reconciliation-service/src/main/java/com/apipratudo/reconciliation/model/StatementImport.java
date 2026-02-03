package com.apipratudo.reconciliation.model;

import java.time.Instant;
import java.time.LocalDate;

public record StatementImport(
    String id,
    String tenantId,
    String accountId,
    String format,
    LocalDate periodStart,
    LocalDate periodEnd,
    Instant createdAt,
    int totalTransactions,
    long totalCreditsCents,
    long totalDebitsCents
) {
}
