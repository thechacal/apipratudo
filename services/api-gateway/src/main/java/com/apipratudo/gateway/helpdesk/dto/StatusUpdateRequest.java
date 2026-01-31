package com.apipratudo.gateway.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusUpdateRequest(
    @NotBlank String status
) {
}
