package com.apipratudo.helpdesk.model;

import java.time.Instant;
import java.util.List;

public record Ticket(
    String id,
    String tenantId,
    TicketStatus status,
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
