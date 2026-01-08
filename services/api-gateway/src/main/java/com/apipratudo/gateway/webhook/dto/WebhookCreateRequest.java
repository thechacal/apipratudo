package com.apipratudo.gateway.webhook.dto;

import jakarta.validation.constraints.NotBlank;

public record WebhookCreateRequest(
    @NotBlank(message = "must not be blank")
    String targetUrl,
    @NotBlank(message = "must not be blank")
    String eventType
) {
}
