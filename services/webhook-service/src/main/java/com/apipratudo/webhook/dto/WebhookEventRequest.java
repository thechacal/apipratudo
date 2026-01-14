package com.apipratudo.webhook.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record WebhookEventRequest(
    @NotBlank String event,
    @NotBlank String apiKey,
    @NotNull @Valid WebhookEventData data,
    Instant occurredAt
) {
}
