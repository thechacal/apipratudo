package com.apipratudo.portal.dto;

public record KeyRequestResponse(
    String apiKey,
    Plan plan,
    ApiKeyLimits limits,
    ApiKeyCredits credits,
    String docsUrl,
    String gatewayUrl
) {
}
