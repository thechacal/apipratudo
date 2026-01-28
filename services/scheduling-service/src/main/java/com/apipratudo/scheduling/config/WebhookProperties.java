package com.apipratudo.scheduling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "webhook")
public class WebhookProperties {

  private String baseUrl = "http://localhost:8082";
  private long timeoutMs = 3000;
  private String serviceToken;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public long getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public String getServiceToken() {
    return serviceToken;
  }

  public void setServiceToken(String serviceToken) {
    this.serviceToken = serviceToken;
  }
}
