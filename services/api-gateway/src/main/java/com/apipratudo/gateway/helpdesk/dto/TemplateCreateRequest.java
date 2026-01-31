package com.apipratudo.gateway.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record TemplateCreateRequest(
    @NotBlank String name,
    @NotBlank String body
) {
}
