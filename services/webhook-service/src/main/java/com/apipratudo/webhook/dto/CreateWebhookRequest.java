package com.apipratudo.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record CreateWebhookRequest(
    @NotBlank(message = "must not be blank")
    @Pattern(regexp = "https?://.+", message = "must be a valid http/https URL")
    String targetUrl,
    @NotEmpty(message = "must not be empty")
    List<@NotBlank(message = "must not be blank") String> events,
    String secret
) {
}
