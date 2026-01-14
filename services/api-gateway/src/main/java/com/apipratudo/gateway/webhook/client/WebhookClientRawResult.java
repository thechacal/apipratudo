package com.apipratudo.gateway.webhook.client;

public record WebhookClientRawResult(
    int statusCode,
    String body
) {
}
