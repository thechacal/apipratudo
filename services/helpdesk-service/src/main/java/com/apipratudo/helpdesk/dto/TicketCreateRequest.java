package com.apipratudo.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record TicketCreateRequest(
    @NotBlank String customerWaId
) {
}
