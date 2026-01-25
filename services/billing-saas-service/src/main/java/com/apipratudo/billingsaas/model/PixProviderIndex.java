package com.apipratudo.billingsaas.model;

import java.time.Instant;

public record PixProviderIndex(
    String provider,
    String providerChargeId,
    String tenantId,
    String chargeId,
    Instant createdAt
) {
}
