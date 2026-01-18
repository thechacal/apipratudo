package com.apipratudo.billing.dto;

import jakarta.validation.constraints.Min;

public record BillingChargeRequest(
    String apiKey,
    String apiKeyHash,
    String packageName,
    @Min(1) long credits,
    @Min(1) int amountCents,
    String description,
    Payer payer
) {
  public record Payer(
      String name,
      String email,
      String taxId,
      String phoneArea,
      String phoneNumber
  ) {
  }
}
