package com.apipratudo.gateway.idempotency;

public record IdempotencyRequest(
    String method,
    String path,
    String idempotencyKey,
    String requestHash
) {
}
