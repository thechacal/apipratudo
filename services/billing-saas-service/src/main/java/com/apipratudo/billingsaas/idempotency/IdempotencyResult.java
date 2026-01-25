package com.apipratudo.billingsaas.idempotency;

import java.util.Map;

public record IdempotencyResult(
    int statusCode,
    String responseBodyJson,
    Map<String, String> responseHeaders,
    boolean replayed
) {
}
