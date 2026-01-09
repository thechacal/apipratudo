package com.apipratudo.gateway.webhook.dto;

import com.apipratudo.gateway.webhook.model.WebhookStatus;
public record WebhookCreateResponse(
    String id,
    WebhookStatus status
) {
}
