package com.apipratudo.gateway.timemania.client;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "timemania")
@Validated
public class TimemaniaResultsClientProperties {

  @NotBlank
  private String baseUrl = "http://localhost:8088";

  @Min(100)
  private long timeoutMs = 60000;

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
}
