package com.apipratudo.billingsaas.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record Charge(
    String id,
    String customerId,
    long amountCents,
    String currency,
    String description,
    LocalDate dueDate,
    Recurrence recurrence,
    Map<String, String> metadata,
    ChargeStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant paidAt,
    PixData pix,
    String providerChargeId,
    String tenantId
) {
}
