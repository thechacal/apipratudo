package com.apipratudo.helpdesk.dto;

import java.time.Instant;

public record MessageResponse(
    String id,
    String ticketId,
    String direction,
    String text,
    Instant createdAt,
    String providerMessageId
) {
}
