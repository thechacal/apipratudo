package com.apipratudo.gateway.webhook.dto;

import java.util.List;

public record DeliveryListResponse(
    List<DeliveryResponse> items,
    int page,
    int size,
    long total
) {
}
