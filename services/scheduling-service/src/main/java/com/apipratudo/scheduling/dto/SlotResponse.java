package com.apipratudo.scheduling.dto;

import java.time.Instant;

public record SlotResponse(
    Instant startAt,
    Instant endAt
) {
}
