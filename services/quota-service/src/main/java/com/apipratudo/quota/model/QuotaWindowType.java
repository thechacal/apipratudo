package com.apipratudo.quota.model;

import java.time.Duration;

public enum QuotaWindowType {
  MINUTE(Duration.ofMinutes(1)),
  DAY(Duration.ofDays(1));

  private final Duration duration;

  QuotaWindowType(Duration duration) {
    this.duration = duration;
  }

  public Duration duration() {
    return duration;
  }
}
