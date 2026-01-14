package com.apipratudo.webhook.dto;

import java.util.List;

public record WebhookListResponse(
    List<WebhookResponse> items,
    String nextCursor
) {
}
