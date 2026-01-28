package com.apipratudo.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ServiceCreateRequest(
    @NotBlank String name,
    @Min(1) int durationMin,
    @Min(0) int prepMin,
    @Min(0) int bufferMin,
    @Min(0) long noShowFeeCents,
    @NotNull Boolean active
) {
}
