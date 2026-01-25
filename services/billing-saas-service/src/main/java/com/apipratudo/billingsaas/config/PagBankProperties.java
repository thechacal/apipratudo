package com.apipratudo.billingsaas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pagbank")
public class PagBankProperties {

  private String sandboxBaseUrl = "https://sandbox.api.pagseguro.com";
  private String productionBaseUrl = "https://api.pagseguro.com";
  private String notificationUrl;
  private int qrTtlSeconds = 3600;
  private String timezone = "America/Sao_Paulo";
  private int timeoutMs = 60000;

  public String getSandboxBaseUrl() {
    return sandboxBaseUrl;
  }

  public void setSandboxBaseUrl(String sandboxBaseUrl) {
    this.sandboxBaseUrl = sandboxBaseUrl;
  }

  public String getProductionBaseUrl() {
    return productionBaseUrl;
  }

  public void setProductionBaseUrl(String productionBaseUrl) {
    this.productionBaseUrl = productionBaseUrl;
  }

  public String getNotificationUrl() {
    return notificationUrl;
  }

  public void setNotificationUrl(String notificationUrl) {
    this.notificationUrl = notificationUrl;
  }

  public int getQrTtlSeconds() {
    return qrTtlSeconds;
  }

  public void setQrTtlSeconds(int qrTtlSeconds) {
    this.qrTtlSeconds = qrTtlSeconds;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public int getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(int timeoutMs) {
    this.timeoutMs = timeoutMs;
  }
}
