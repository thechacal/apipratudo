package com.apipratudo.gateway.reconciliation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MatchRequest(
    @NotBlank String importId,
    @NotNull @Valid RulesetRequest ruleset
) {
}
