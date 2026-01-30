package com.apipratudo.scheduling.model;

import java.time.Instant;

public record AgendaCredits(
    String tenantId,
    String agendaId,
    long remaining,
    Instant updatedAt
) {
  public AgendaCredits withRemaining(long value, Instant now) {
    return new AgendaCredits(tenantId, agendaId, value, now);
  }
}
