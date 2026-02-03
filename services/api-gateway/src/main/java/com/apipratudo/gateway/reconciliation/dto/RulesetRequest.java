package com.apipratudo.gateway.reconciliation.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RulesetRequest(
    @NotEmpty List<String> matchBy,
    Integer dateToleranceDays,
    Long amountToleranceCents,
    Boolean dedupe
) {
}
