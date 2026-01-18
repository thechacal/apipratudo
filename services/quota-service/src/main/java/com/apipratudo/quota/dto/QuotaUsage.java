package com.apipratudo.quota.dto;

import java.time.Instant;

public record QuotaUsage(
    WindowStatus minute,
    WindowStatus day
) {
  public record WindowStatus(
      long limit,
      long used,
      long remaining,
      Instant resetAt
  ) {
  }
}
