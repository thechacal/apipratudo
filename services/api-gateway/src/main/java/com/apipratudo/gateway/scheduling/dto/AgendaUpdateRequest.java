package com.apipratudo.gateway.scheduling.dto;

import jakarta.validation.constraints.Min;

public record AgendaUpdateRequest(
    String name,
    String timezone,
    String workingHoursStart,
    String workingHoursEnd,
    @Min(5) Integer slotStepMin,
    @Min(0) Long noShowFeeCents,
    Boolean active
) {
}
