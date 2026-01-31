package com.apipratudo.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record MessageCreateRequest(
    @NotBlank String direction,
    @NotBlank String text,
    String providerMessageId
) {
}
