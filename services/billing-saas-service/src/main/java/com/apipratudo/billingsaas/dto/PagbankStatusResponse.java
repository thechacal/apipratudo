package com.apipratudo.billingsaas.dto;

import java.time.Instant;

public class PagbankStatusResponse {

  private boolean connected;
  private String environment;
  private Instant lastVerifiedAt;
  private String fingerprint;

  public boolean isConnected() {
    return connected;
  }

  public void setConnected(boolean connected) {
    this.connected = connected;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public Instant getLastVerifiedAt() {
    return lastVerifiedAt;
  }

  public void setLastVerifiedAt(Instant lastVerifiedAt) {
    this.lastVerifiedAt = lastVerifiedAt;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }
}
