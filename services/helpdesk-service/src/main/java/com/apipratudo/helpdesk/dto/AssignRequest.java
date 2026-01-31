package com.apipratudo.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignRequest(
    @NotBlank String assigneeUserId
) {
}
