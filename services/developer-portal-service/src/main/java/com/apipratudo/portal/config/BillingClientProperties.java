package com.apipratudo.portal.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.billing")
@Validated
public class BillingClientProperties {

  @NotBlank
  private String baseUrl = "http://localhost:8095";

  private String serviceToken;

  @Min(100)
  private long timeoutMs = 3000;

  @Min(1)
  private int premiumPriceCents = 4900;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getServiceToken() {
    return serviceToken;
  }

  public void setServiceToken(String serviceToken) {
    this.serviceToken = serviceToken;
  }

  public long getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public int getPremiumPriceCents() {
    return premiumPriceCents;
  }

  public void setPremiumPriceCents(int premiumPriceCents) {
    this.premiumPriceCents = premiumPriceCents;
  }
}
