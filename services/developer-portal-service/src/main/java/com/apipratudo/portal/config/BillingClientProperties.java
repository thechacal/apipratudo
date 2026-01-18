package com.apipratudo.portal.config;

import jakarta.validation.Valid;
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

  @Valid
  private CreditPackageConfig start = new CreditPackageConfig(1990, 50000);

  @Valid
  private CreditPackageConfig pro = new CreditPackageConfig(4990, 200000);

  @Valid
  private CreditPackageConfig scale = new CreditPackageConfig(9990, 500000);

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

  public CreditPackageConfig getStart() {
    return start;
  }

  public void setStart(CreditPackageConfig start) {
    this.start = start;
  }

  public CreditPackageConfig getPro() {
    return pro;
  }

  public void setPro(CreditPackageConfig pro) {
    this.pro = pro;
  }

  public CreditPackageConfig getScale() {
    return scale;
  }

  public void setScale(CreditPackageConfig scale) {
    this.scale = scale;
  }

  public record CreditPackageConfig(@Min(1) int priceCents, @Min(1) long credits) {
  }
}
