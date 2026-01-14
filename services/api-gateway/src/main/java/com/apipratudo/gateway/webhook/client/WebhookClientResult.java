package com.apipratudo.gateway.webhook.client;

import com.apipratudo.gateway.webhook.dto.WebhookCreateResponse;

public record WebhookClientResult(
    int statusCode,
    WebhookCreateResponse response
) {
}
