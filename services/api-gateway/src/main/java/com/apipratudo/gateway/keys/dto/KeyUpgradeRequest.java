package com.apipratudo.gateway.keys.dto;

import jakarta.validation.constraints.NotBlank;

public record KeyUpgradeRequest(
    @NotBlank String plan
) {
}
