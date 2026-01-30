package com.apipratudo.gateway.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgendaCreateRequest(
    @NotBlank String name,
    @NotBlank String timezone,
    @NotBlank String workingHoursStart,
    @NotBlank String workingHoursEnd,
    @Min(5) int slotStepMin,
    @Min(0) long noShowFeeCents,
    @NotNull Boolean active
) {
}
