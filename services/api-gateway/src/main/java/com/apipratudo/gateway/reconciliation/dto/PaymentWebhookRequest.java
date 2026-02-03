package com.apipratudo.gateway.reconciliation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public record PaymentWebhookRequest(
    @NotBlank String eventId,
    @NotNull Instant paidAt,
    @NotNull Long amountCents,
    @NotBlank String reference,
    String providerPaymentId,
    Map<String, Object> raw
) {
}
