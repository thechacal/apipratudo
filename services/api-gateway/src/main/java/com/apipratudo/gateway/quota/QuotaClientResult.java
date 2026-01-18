package com.apipratudo.gateway.quota;

public record QuotaClientResult(
    boolean allowed,
    String reason,
    int statusCode,
    String error,
    String plan
) {
}
