package com.apipratudo.scheduling.model;

import java.time.Instant;

public record ServiceDef(
    String id,
    String tenantId,
    String name,
    int durationMin,
    int prepMin,
    int bufferMin,
    long noShowFeeCents,
    boolean active,
    Instant createdAt
) {
}
