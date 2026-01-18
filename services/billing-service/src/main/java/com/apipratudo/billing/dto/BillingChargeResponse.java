package com.apipratudo.billing.dto;

public record BillingChargeResponse(
    String chargeId,
    String status,
    int amountCents,
    String amount,
    long credits,
    String packageName,
    String expiresAt,
    String pixCopyPaste,
    String qrCodeBase64
) {
}
