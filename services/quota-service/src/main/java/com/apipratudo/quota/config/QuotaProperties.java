package com.apipratudo.quota.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.quota")
public class QuotaProperties {

  private long idempotencyTtlSeconds = 86400;

  public long getIdempotencyTtlSeconds() {
    return idempotencyTtlSeconds;
  }

  public void setIdempotencyTtlSeconds(long idempotencyTtlSeconds) {
    this.idempotencyTtlSeconds = idempotencyTtlSeconds;
  }
}
