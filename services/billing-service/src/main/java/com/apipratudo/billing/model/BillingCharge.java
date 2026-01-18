package com.apipratudo.billing.model;

import java.time.Instant;

public record BillingCharge(
    String chargeId,
    String referenceId,
    String apiKeyHash,
    String apiKeyPrefix,
    String plan,
    Integer amountCents,
    String description,
    String statusCharge,
    String statusTop,
    Boolean paid,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt,
    String pixCopyPaste,
    String qrCodeBase64,
    Instant paidAt,
    Boolean premiumActivated
) {
}
