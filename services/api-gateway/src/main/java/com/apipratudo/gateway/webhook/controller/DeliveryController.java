package com.apipratudo.gateway.webhook.controller;

import com.apipratudo.gateway.webhook.dto.DeliveryListResponse;
import com.apipratudo.gateway.webhook.dto.DeliveryResponse;
import com.apipratudo.gateway.webhook.service.DeliveryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/deliveries")
@Validated
@Tag(name = "deliveries")
public class DeliveryController {

  private final DeliveryService deliveryService;

  public DeliveryController(DeliveryService deliveryService) {
    this.deliveryService = deliveryService;
  }

  @GetMapping
  public DeliveryListResponse list(
      @RequestParam(required = false) String webhookId,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size
  ) {
    return deliveryService.list(webhookId, page, size);
  }

  @GetMapping("/{id}")
  public DeliveryResponse get(@PathVariable String id) {
    return deliveryService.get(id);
  }

  @PostMapping("/{id}/retry")
  public ResponseEntity<DeliveryResponse> retry(@PathVariable String id) {
    DeliveryResponse response = deliveryService.retry(id);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
