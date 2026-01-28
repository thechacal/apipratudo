package com.apipratudo.scheduling.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelRequest(
    @NotBlank String appointmentId,
    String reason
) {
}
