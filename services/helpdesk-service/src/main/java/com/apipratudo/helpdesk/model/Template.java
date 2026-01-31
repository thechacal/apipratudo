package com.apipratudo.helpdesk.model;

import java.time.Instant;

public record Template(
    String id,
    String tenantId,
    String name,
    String body,
    Instant createdAt
) {
}
