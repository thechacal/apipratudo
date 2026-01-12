package com.apipratudo.gateway.webhook.controller;

import com.apipratudo.gateway.webhook.dto.DeliveryTestResponse;
import com.apipratudo.gateway.webhook.dto.WebhookCreateRequest;
import com.apipratudo.gateway.webhook.dto.WebhookCreateResponse;
import com.apipratudo.gateway.webhook.dto.WebhookListResponse;
import com.apipratudo.gateway.webhook.dto.WebhookResponse;
import com.apipratudo.gateway.webhook.dto.WebhookUpdateRequest;
import com.apipratudo.gateway.webhook.service.WebhookService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks")
@Validated
@Tag(name = "webhooks")
@SecurityRequirement(name = "ApiKeyAuth")
public class WebhookController {

  private final WebhookService webhookService;

  public WebhookController(WebhookService webhookService) {
    this.webhookService = webhookService;
  }

  @PostMapping
  public ResponseEntity<WebhookCreateResponse> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody WebhookCreateRequest request
  ) {
    WebhookService.WebhookCreateResult result = webhookService.create(request, idempotencyKey);
    return ResponseEntity.status(result.statusCode()).body(result.response());
  }

  @GetMapping
  public WebhookListResponse list(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size
  ) {
    return webhookService.list(page, size);
  }

  @GetMapping("/{id}")
  public WebhookResponse get(@PathVariable String id) {
    return webhookService.get(id);
  }

  @PatchMapping("/{id}")
  public WebhookResponse update(
      @PathVariable String id,
      @Valid @RequestBody WebhookUpdateRequest request
  ) {
    return webhookService.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    webhookService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/test")
  public ResponseEntity<DeliveryTestResponse> test(@PathVariable String id) {
    DeliveryTestResponse response = webhookService.test(id);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
