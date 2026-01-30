package com.apipratudo.scheduling.client;

public record BillingPixGenerateRequest(
    String chargeId,
    long expiresInSeconds
) {
}
