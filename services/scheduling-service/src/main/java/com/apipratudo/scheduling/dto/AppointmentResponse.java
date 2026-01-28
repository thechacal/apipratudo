package com.apipratudo.scheduling.dto;

import java.time.Instant;

public record AppointmentResponse(
    String appointmentId,
    String serviceId,
    String agendaId,
    String status,
    Instant startAt,
    Instant endAt,
    Instant holdExpiresAt,
    CustomerRequest customer,
    String notes
) {
}
