package com.apipratudo.gateway.quota;

public record QuotaRefundRequest(
    String apiKey,
    String requestId
) {
}
