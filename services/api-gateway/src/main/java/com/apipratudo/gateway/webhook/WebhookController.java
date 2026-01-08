package com.apipratudo.gateway.webhook;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks")
public class WebhookController {

  private final WebhookService webhookService;

  public WebhookController(WebhookService webhookService) {
    this.webhookService = webhookService;
  }

  @PostMapping
  public ResponseEntity<WebhookCreateResponse> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody WebhookCreateRequest request) {
    WebhookService.WebhookCreateResult result = webhookService.create(request, idempotencyKey);
    return ResponseEntity.status(result.statusCode()).body(result.response());
  }
}
