package com.apipratudo.billing.dto;

public record BillingChargeStatusResponse(
    String chargeId,
    String status,
    boolean paid,
    String packageName,
    long creditsAdded,
    Long creditsBalanceAfter,
    boolean creditsApplied
) {
}
