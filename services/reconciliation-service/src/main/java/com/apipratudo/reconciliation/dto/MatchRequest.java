package com.apipratudo.reconciliation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MatchRequest(
    @NotBlank String importId,
    @NotNull @Valid Ruleset ruleset
) {
}
