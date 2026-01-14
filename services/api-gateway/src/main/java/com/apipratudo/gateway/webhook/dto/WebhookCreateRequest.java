package com.apipratudo.gateway.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record WebhookCreateRequest(
    @NotBlank(message = "must not be blank")
    @Pattern(regexp = "https?://.+", message = "must be a valid http/https URL")
    String targetUrl,
    List<String> events,
    String eventType,
    String secret
) {
}
