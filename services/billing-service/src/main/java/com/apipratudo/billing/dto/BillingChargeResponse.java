package com.apipratudo.billing.dto;

public record BillingChargeResponse(
    String chargeId,
    String status,
    int amountCents,
    String amount,
    String packageName,
    long creditsAdded,
    Long creditsBalanceAfter,
    String expiresAt,
    String pixCopyPaste,
    String qrCodeBase64
) {
}
