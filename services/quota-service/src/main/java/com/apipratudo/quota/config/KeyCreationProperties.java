package com.apipratudo.quota.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.key-creation")
@Validated
public class KeyCreationProperties {

  @Min(1)
  private int maxPerEmailPerDay = 1;

  @Min(1)
  private int maxPerOrgPerDay = 3;

  public int getMaxPerEmailPerDay() {
    return maxPerEmailPerDay;
  }

  public void setMaxPerEmailPerDay(int maxPerEmailPerDay) {
    this.maxPerEmailPerDay = maxPerEmailPerDay;
  }

  public int getMaxPerOrgPerDay() {
    return maxPerOrgPerDay;
  }

  public void setMaxPerOrgPerDay(int maxPerOrgPerDay) {
    this.maxPerOrgPerDay = maxPerOrgPerDay;
  }
}
