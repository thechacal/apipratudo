package com.apipratudo.billingsaas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

  private String masterKeyBase64;

  public String getMasterKeyBase64() {
    return masterKeyBase64;
  }

  public void setMasterKeyBase64(String masterKeyBase64) {
    this.masterKeyBase64 = masterKeyBase64;
  }
}
