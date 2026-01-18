package com.apipratudo.billing.dto;

public record BillingChargeResponse(
    String chargeId,
    String status,
    String expiresAt,
    String pixCopyPaste,
    String qrCodeBase64
) {
}
