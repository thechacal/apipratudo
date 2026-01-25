package com.apipratudo.billingsaas.model;

import java.time.LocalDate;

public record ReportSummary(
    LocalDate from,
    LocalDate to,
    long countTotal,
    long countPaid,
    long countPending,
    long totalCents,
    long paidCents,
    long pendingCents
) {
}
