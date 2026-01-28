package com.apipratudo.scheduling.dto;

public record ConfirmResponse(
    String appointmentId,
    String status
) {
}
