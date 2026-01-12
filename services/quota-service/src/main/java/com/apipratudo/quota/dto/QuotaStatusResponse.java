package com.apipratudo.quota.dto;

import java.time.Instant;

public record QuotaStatusResponse(
    String apiKeyId,
    QuotaWindowStatus minute,
    QuotaWindowStatus day
) {

  public record QuotaWindowStatus(
      long limit,
      long used,
      long remaining,
      Instant resetAt
  ) {
  }
}
