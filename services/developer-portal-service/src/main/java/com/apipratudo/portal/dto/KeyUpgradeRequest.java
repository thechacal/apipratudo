package com.apipratudo.portal.dto;

import jakarta.validation.constraints.NotBlank;

public record KeyUpgradeRequest(
    @NotBlank String plan
) {
}
