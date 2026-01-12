package com.apipratudo.quota.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApiKeyCreateRequest(
    @NotBlank String name,
    @NotBlank String owner,
    @NotNull @Valid ApiKeyLimits limits
) {
}
