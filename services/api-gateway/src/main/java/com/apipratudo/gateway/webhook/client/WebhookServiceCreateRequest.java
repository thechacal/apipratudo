package com.apipratudo.gateway.webhook.client;

import java.util.List;

public record WebhookServiceCreateRequest(
    String targetUrl,
    List<String> events,
    String secret
) {
}
