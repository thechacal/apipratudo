package com.apipratudo.scheduling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scheduling.no-show")
public class NoShowProperties {

  private boolean enabled = true;
  private int graceMin = 15;
  private long scanIntervalMs = 60000;
  private int maxPerRun = 100;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getGraceMin() {
    return graceMin;
  }

  public void setGraceMin(int graceMin) {
    this.graceMin = graceMin;
  }

  public long getScanIntervalMs() {
    return scanIntervalMs;
  }

  public void setScanIntervalMs(long scanIntervalMs) {
    this.scanIntervalMs = scanIntervalMs;
  }

  public int getMaxPerRun() {
    return maxPerRun;
  }

  public void setMaxPerRun(int maxPerRun) {
    this.maxPerRun = maxPerRun;
  }
}
