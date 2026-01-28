package com.apipratudo.scheduling.dto;

public record NotifyResponse(
    boolean ok,
    String appointmentId,
    String type
) {
}
