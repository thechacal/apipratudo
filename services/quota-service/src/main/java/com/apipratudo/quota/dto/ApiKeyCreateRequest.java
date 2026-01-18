package com.apipratudo.quota.dto;

import com.apipratudo.quota.model.Plan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApiKeyCreateRequest(
    @NotBlank String name,
    @NotBlank String owner,
    @Email String ownerEmail,
    String orgName,
    Plan plan,
    @NotNull @Valid ApiKeyLimits limits
) {
}
