package com.apipratudo.reconciliation.service;

import com.apipratudo.reconciliation.model.BankTransaction;
import java.time.LocalDate;
import java.util.List;

public record ParsedStatement(String format, List<BankTransaction> transactions, LocalDate periodStart,
                              LocalDate periodEnd, long totalCreditsCents, long totalDebitsCents) {
}
