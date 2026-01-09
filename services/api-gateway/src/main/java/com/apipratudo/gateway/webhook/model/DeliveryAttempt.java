package com.apipratudo.gateway.webhook.model;

import java.time.Instant;

public record DeliveryAttempt(
    int attempt,
    Integer responseCode,
    String errorMessage,
    Instant occurredAt
) {
}
