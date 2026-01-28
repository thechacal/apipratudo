package com.apipratudo.scheduling.idempotency;

public record IdempotencyRequest(
    String tenantId,
    String method,
    String path,
    String idempotencyKey,
    String requestHash
) {
}
