package com.apipratudo.scheduling.dto;

import java.util.List;

public record AgendaResponse(
    String agendaId,
    String from,
    String to,
    List<AppointmentResponse> appointments
) {
}
