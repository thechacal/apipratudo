package com.apipratudo.portal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BillingChargeRequest(
    String apiKey,
    String apiKeyHash,
    @NotBlank String plan,
    @Min(1) int amountCents,
    String description,
    Payer payer
) {
  public record Payer(
      String name,
      String email,
      String taxId
  ) {
  }
}
