package com.apipratudo.billing.dto;

public record BillingWebhookResponse(boolean ok, String mode, String warning) {
}
