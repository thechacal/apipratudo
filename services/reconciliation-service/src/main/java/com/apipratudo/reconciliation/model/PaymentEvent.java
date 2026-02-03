package com.apipratudo.reconciliation.model;

import java.time.Instant;
import java.util.Map;

public record PaymentEvent(
    String id,
    String tenantId,
    Instant paidAt,
    long amountCents,
    String reference,
    String providerPaymentId,
    Map<String, Object> raw
) {
}
