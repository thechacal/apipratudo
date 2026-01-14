package com.apipratudo.webhook.controller;

import com.apipratudo.webhook.dto.CreateWebhookRequest;
import com.apipratudo.webhook.dto.WebhookListResponse;
import com.apipratudo.webhook.dto.WebhookResponse;
import com.apipratudo.webhook.error.UnauthorizedException;
import com.apipratudo.webhook.service.WebhookService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks")
@Validated
public class WebhookController {

  private final WebhookService webhookService;

  public WebhookController(WebhookService webhookService) {
    this.webhookService = webhookService;
  }

  @PostMapping
  public ResponseEntity<WebhookResponse> create(
      @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateWebhookRequest request
  ) {
    if (!StringUtils.hasText(apiKey)) {
      throw new UnauthorizedException("Missing X-Api-Key");
    }
    WebhookResponse response = webhookService.createWebhook(apiKey.trim(), idempotencyKey, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  public WebhookListResponse list(
      @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(required = false) String cursor
  ) {
    if (!StringUtils.hasText(apiKey)) {
      throw new UnauthorizedException("Missing X-Api-Key");
    }
    return webhookService.listWebhooks(apiKey.trim(), limit, cursor);
  }

  @GetMapping("/{id}")
  public WebhookResponse get(
      @PathVariable String id,
      @RequestHeader(value = "X-Api-Key", required = false) String apiKey
  ) {
    if (!StringUtils.hasText(apiKey)) {
      throw new UnauthorizedException("Missing X-Api-Key");
    }
    return webhookService.getWebhook(apiKey.trim(), id);
  }
}
