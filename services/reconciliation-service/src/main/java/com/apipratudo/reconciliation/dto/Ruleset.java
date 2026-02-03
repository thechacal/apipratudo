package com.apipratudo.reconciliation.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record Ruleset(
    @NotEmpty List<String> matchBy,
    Integer dateToleranceDays,
    Long amountToleranceCents,
    Boolean dedupe
) {
}
