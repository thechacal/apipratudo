package com.apipratudo.scheduling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.idempotency")
public class IdempotencyProperties {

  private long ttlSeconds = 86400;

  public long getTtlSeconds() {
    return ttlSeconds;
  }

  public void setTtlSeconds(long ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }
}
