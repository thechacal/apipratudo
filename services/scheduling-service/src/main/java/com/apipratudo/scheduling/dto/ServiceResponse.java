package com.apipratudo.scheduling.dto;

import java.time.Instant;

public record ServiceResponse(
    String id,
    String name,
    int durationMin,
    int prepMin,
    int bufferMin,
    long noShowFeeCents,
    boolean active,
    Instant createdAt
) {
}
