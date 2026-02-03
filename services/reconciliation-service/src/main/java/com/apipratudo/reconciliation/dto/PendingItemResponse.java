package com.apipratudo.reconciliation.dto;

public record PendingItemResponse(
    String type,
    String itemId,
    String date,
    long amountCents,
    String reference,
    String description
) {
}
