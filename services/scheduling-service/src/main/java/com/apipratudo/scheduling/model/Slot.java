package com.apipratudo.scheduling.model;

import java.time.Instant;

public record Slot(
    Instant startAt,
    Instant endAt
) {
}
