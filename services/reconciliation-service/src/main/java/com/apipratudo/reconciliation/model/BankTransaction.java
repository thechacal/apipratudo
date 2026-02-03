package com.apipratudo.reconciliation.model;

import java.time.LocalDate;

public record BankTransaction(
    String id,
    String tenantId,
    String importId,
    LocalDate date,
    long amountCents,
    String type,
    String description,
    String reference
) {
}
