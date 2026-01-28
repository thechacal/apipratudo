package com.apipratudo.scheduling.dto;

import jakarta.validation.constraints.NotBlank;

public record NotifyRequest(
    @NotBlank String appointmentId,
    @NotBlank String type
) {
}
