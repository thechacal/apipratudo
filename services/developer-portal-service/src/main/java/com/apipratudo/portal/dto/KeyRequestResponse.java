package com.apipratudo.portal.dto;

public record KeyRequestResponse(
    String apiKey,
    Plan plan,
    ApiKeyLimits limits,
    String docsUrl,
    String gatewayUrl
) {
}
