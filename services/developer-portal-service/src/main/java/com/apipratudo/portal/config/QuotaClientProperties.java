package com.apipratudo.portal.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.quota")
@Validated
public class QuotaClientProperties {

  @NotBlank
  private String baseUrl = "http://localhost:8081";

  private String portalToken;

  @Min(100)
  private long timeoutMs = 3000;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getPortalToken() {
    return portalToken;
  }

  public void setPortalToken(String portalToken) {
    this.portalToken = portalToken;
  }

  public long getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }
}
