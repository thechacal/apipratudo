package com.apipratudo.scheduling.dto;

import jakarta.validation.constraints.NotBlank;

public record AttendedRequest(
    @NotBlank String appointmentId
) {
}
