package com.apipratudo.gateway.webhook.dto;

import com.apipratudo.gateway.webhook.model.WebhookStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

public record WebhookUpdateRequest(
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    String targetUrl,
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    String eventType,
    WebhookStatus status
) {

  @AssertTrue(message = "at least one field must be provided")
  public boolean isAnyFieldProvided() {
    return targetUrl != null || eventType != null || status != null;
  }
}
