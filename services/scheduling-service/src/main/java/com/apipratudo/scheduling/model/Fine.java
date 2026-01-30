package com.apipratudo.scheduling.model;

import java.time.Instant;

public record Fine(
    String id,
    String tenantId,
    String agendaId,
    String appointmentId,
    long amountCents,
    FineStatus status,
    Instant createdAt,
    Instant updatedAt
) {
  public Fine withStatus(FineStatus status, Instant now) {
    return new Fine(
        id,
        tenantId,
        agendaId,
        appointmentId,
        amountCents,
        status,
        createdAt,
        now
    );
  }
}
