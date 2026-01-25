package com.apipratudo.billingsaas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class PixGenerateRequest {

  @NotBlank
  private String chargeId;

  @Min(60)
  private Long expiresInSeconds;

  public String getChargeId() {
    return chargeId;
  }

  public void setChargeId(String chargeId) {
    this.chargeId = chargeId;
  }

  public Long getExpiresInSeconds() {
    return expiresInSeconds;
  }

  public void setExpiresInSeconds(Long expiresInSeconds) {
    this.expiresInSeconds = expiresInSeconds;
  }
}
