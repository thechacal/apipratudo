package com.apipratudo.scheduling.client;

import java.time.Instant;

public record BillingPixGenerateResponse(
    String chargeId,
    String status,
    String providerChargeId,
    String pixCopyPaste,
    Instant expiresAt
) {
}
