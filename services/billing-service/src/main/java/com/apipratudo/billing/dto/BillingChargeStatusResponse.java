package com.apipratudo.billing.dto;

public record BillingChargeStatusResponse(
    String chargeId,
    String status,
    boolean paid,
    String plan,
    boolean premiumActivated
) {
}
