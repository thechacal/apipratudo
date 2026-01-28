package com.apipratudo.scheduling.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRequest(
    @NotBlank String appointmentId
) {
}
