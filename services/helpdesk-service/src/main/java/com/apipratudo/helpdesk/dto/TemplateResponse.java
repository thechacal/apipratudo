package com.apipratudo.helpdesk.dto;

import java.time.Instant;

public record TemplateResponse(
    String id,
    String name,
    String body,
    Instant createdAt
) {
}
