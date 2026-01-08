package com.apipratudo.gateway.webhook.dto;

import java.util.List;

public record WebhookListResponse(
    List<WebhookResponse> items,
    int page,
    int size,
    long total
) {
}
