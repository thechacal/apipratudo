package com.apipratudo.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.quota")
public class QuotaClientProperties {

  private String baseUrl = "http://localhost:8081";
  private String internalToken;
  private int timeoutMs = 3000;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getInternalToken() {
    return internalToken;
  }

  public void setInternalToken(String internalToken) {
    this.internalToken = internalToken;
  }

  public int getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(int timeoutMs) {
    this.timeoutMs = timeoutMs;
  }
}
