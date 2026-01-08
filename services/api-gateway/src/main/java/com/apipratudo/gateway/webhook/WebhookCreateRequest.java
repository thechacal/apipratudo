package com.apipratudo.gateway.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WebhookCreateRequest(
    @NotBlank(message = "is required")
    @Pattern(regexp = "^https?://.+", message = "must be a valid http(s) URL")
    String targetUrl,
    @NotBlank(message = "is required")
    String eventType
) {
}
