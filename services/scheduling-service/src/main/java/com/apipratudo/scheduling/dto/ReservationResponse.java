package com.apipratudo.scheduling.dto;

import java.time.Instant;

public record ReservationResponse(
    String appointmentId,
    String status,
    Instant holdExpiresAt,
    Instant startAt,
    Instant endAt
) {
}
