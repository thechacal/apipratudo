package com.apipratudo.gateway.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentValidateRequest(
    @NotBlank String tipo,
    @NotBlank String documento
) {
}
