package com.apipratudo.scheduling.client;

import java.time.Instant;

public record WebhookEventRequest(
    String event,
    String apiKey,
    WebhookEventData data,
    Instant occurredAt
) {
}
