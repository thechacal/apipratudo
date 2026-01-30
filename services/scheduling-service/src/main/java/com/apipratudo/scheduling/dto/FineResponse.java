package com.apipratudo.scheduling.dto;

import java.time.Instant;

public record FineResponse(
    String id,
    String agendaId,
    String appointmentId,
    long amountCents,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
