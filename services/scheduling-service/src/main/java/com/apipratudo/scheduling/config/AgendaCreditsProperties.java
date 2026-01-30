package com.apipratudo.scheduling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scheduling.credits")
public class AgendaCreditsProperties {

  private long defaultCredits = 0;
  private String packageName = "START";
  private long packageCredits = 10;
  private long packagePriceCents = 1990;
  private String packageDescription = "Creditos de agenda";
  private String packageCurrency = "BRL";
  private long pixExpiresInSeconds = 3600;

  public long getDefaultCredits() {
    return defaultCredits;
  }

  public void setDefaultCredits(long defaultCredits) {
    this.defaultCredits = defaultCredits;
  }

  public String getPackageName() {
    return packageName;
  }

  public void setPackageName(String packageName) {
    this.packageName = packageName;
  }

  public long getPackageCredits() {
    return packageCredits;
  }

  public void setPackageCredits(long packageCredits) {
    this.packageCredits = packageCredits;
  }

  public long getPackagePriceCents() {
    return packagePriceCents;
  }

  public void setPackagePriceCents(long packagePriceCents) {
    this.packagePriceCents = packagePriceCents;
  }

  public String getPackageDescription() {
    return packageDescription;
  }

  public void setPackageDescription(String packageDescription) {
    this.packageDescription = packageDescription;
  }

  public String getPackageCurrency() {
    return packageCurrency;
  }

  public void setPackageCurrency(String packageCurrency) {
    this.packageCurrency = packageCurrency;
  }

  public long getPixExpiresInSeconds() {
    return pixExpiresInSeconds;
  }

  public void setPixExpiresInSeconds(long pixExpiresInSeconds) {
    this.pixExpiresInSeconds = pixExpiresInSeconds;
  }
}
