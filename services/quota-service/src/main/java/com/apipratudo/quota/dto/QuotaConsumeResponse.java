package com.apipratudo.quota.dto;

import com.apipratudo.quota.model.Plan;
import java.time.Instant;

public record QuotaConsumeResponse(
    boolean allowed,
    QuotaReason reason,
    Long limit,
    Long remaining,
    Instant resetAt,
    String error,
    String message,
    Plan plan,
    ApiKeyLimits limits,
    QuotaUsage usage,
    UpgradeHint upgrade
) {
  public record UpgradeHint(String endpoint, String method) {
  }
}
