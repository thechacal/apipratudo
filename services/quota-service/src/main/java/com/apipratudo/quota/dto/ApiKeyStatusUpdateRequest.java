package com.apipratudo.quota.dto;

import com.apipratudo.quota.model.ApiKeyStatus;
import jakarta.validation.constraints.NotNull;

public record ApiKeyStatusUpdateRequest(
    @NotNull ApiKeyStatus status
) {
}
