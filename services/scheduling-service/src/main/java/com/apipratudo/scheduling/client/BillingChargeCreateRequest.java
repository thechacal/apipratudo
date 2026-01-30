package com.apipratudo.scheduling.client;

import java.time.LocalDate;
import java.util.Map;

public record BillingChargeCreateRequest(
    String customerId,
    long amountCents,
    String currency,
    String description,
    LocalDate dueDate,
    Map<String, String> metadata
) {
}
