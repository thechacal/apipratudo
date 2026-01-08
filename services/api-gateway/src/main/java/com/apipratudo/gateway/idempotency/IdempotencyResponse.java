package com.apipratudo.gateway.idempotency;

import java.util.Map;

public record IdempotencyResponse(
    int statusCode,
    String responseBodyJson,
    Map<String, String> responseHeaders
) {
}
