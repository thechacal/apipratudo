package com.apipratudo.scheduling.dto;

import java.time.Instant;

public record AgendaInfoResponse(
    String id,
    String name,
    String timezone,
    String workingHoursStart,
    String workingHoursEnd,
    int slotStepMin,
    long noShowFeeCents,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    long creditsRemaining
) {
}
