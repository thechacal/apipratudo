package com.apipratudo.billingsaas.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public class PixWebhookRequest {

  @NotBlank
  private String provider;

  @NotBlank
  private String providerChargeId;

  @NotBlank
  private String event;

  private Instant paidAt;

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getProviderChargeId() {
    return providerChargeId;
  }

  public void setProviderChargeId(String providerChargeId) {
    this.providerChargeId = providerChargeId;
  }

  public String getEvent() {
    return event;
  }

  public void setEvent(String event) {
    this.event = event;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(Instant paidAt) {
    this.paidAt = paidAt;
  }
}
