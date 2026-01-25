package com.apipratudo.billingsaas.idempotency;

public record IdempotencyRequest(
    String tenantId,
    String method,
    String path,
    String idempotencyKey,
    String requestHash
) {
}
