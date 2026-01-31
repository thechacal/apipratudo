package com.apipratudo.helpdesk.model;

import java.time.Instant;

public record Message(
    String id,
    String tenantId,
    String ticketId,
    MessageDirection direction,
    String text,
    Instant createdAt,
    String providerMessageId
) {
}
