package com.apipratudo.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record TemplateCreateRequest(
    @NotBlank String name,
    @NotBlank String body
) {
}
