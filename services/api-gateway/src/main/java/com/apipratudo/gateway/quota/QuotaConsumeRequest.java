package com.apipratudo.gateway.quota;

public record QuotaConsumeRequest(
    String apiKey,
    String requestId,
    String route,
    int cost
) {
}
