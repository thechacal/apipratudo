package com.apipratudo.gateway.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.deliveries")
@Validated
public class DeliveryProperties {

  private String collection = "deliveries";
  private int listLimit = 200;
  @Min(1)
  private int maxAttempts = 5;
  @Min(0)
  private long initialBackoffMs = 500;
  @Min(0)
  private long maxBackoffMs = 30000;
  @Min(1)
  private long timeoutMs = 5000;
  private boolean retryOn5xx = true;
  private boolean retryOn429 = true;

  public String getCollection() {
    return collection;
  }

  public void setCollection(String collection) {
    this.collection = collection;
  }

  public int getListLimit() {
    return listLimit;
  }

  public void setListLimit(int listLimit) {
    this.listLimit = listLimit;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public long getInitialBackoffMs() {
    return initialBackoffMs;
  }

  public void setInitialBackoffMs(long initialBackoffMs) {
    this.initialBackoffMs = initialBackoffMs;
  }

  public long getMaxBackoffMs() {
    return maxBackoffMs;
  }

  public void setMaxBackoffMs(long maxBackoffMs) {
    this.maxBackoffMs = maxBackoffMs;
  }

  public long getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public boolean isRetryOn5xx() {
    return retryOn5xx;
  }

  public void setRetryOn5xx(boolean retryOn5xx) {
    this.retryOn5xx = retryOn5xx;
  }

  public boolean isRetryOn429() {
    return retryOn429;
  }

  public void setRetryOn429(boolean retryOn429) {
    this.retryOn429 = retryOn429;
  }
}
