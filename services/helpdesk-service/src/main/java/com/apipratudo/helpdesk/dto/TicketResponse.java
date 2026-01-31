package com.apipratudo.helpdesk.dto;

import java.time.Instant;
import java.util.List;

public record TicketResponse(
    String id,
    String status,
    String assigneeUserId,
    String customerWaId,
    Instant createdAt,
    Instant updatedAt,
    Instant slaFirstResponseDueAt,
    Instant firstResponseAt,
    String autoSummary,
    List<String> intentTags
) {
}
