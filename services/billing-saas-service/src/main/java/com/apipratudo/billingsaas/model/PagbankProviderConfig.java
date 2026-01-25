package com.apipratudo.billingsaas.model;

import java.time.Instant;

public record PagbankProviderConfig(
    String tenantId,
    boolean enabled,
    PagbankEnvironment environment,
    EncryptedValue token,
    EncryptedValue webhookToken,
    String fingerprint,
    Instant createdAt,
    Instant updatedAt,
    Instant lastVerifiedAt
) {
}
