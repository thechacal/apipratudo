package com.apipratudo.scheduling.idempotency;

import java.util.Map;

public record IdempotencyResponse(
    int statusCode,
    String responseBodyJson,
    Map<String, String> responseHeaders
) {
}
