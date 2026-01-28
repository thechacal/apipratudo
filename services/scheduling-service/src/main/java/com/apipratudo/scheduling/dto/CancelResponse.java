package com.apipratudo.scheduling.dto;

public record CancelResponse(
    String appointmentId,
    String status,
    long cancellationFeeCents
) {
}
