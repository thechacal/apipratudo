package com.apipratudo.quota.controller;

import com.apipratudo.quota.dto.ActivatePremiumRequest;
import com.apipratudo.quota.dto.ActivatePremiumResponse;
import com.apipratudo.quota.service.ApiKeyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/subscriptions")
@Validated
@Tag(name = "subscriptions")
@SecurityRequirement(name = "AdminToken")
@SecurityRequirement(name = "InternalToken")
public class SubscriptionController {

  private final ApiKeyService apiKeyService;

  public SubscriptionController(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @PostMapping("/activate-premium")
  public ResponseEntity<ActivatePremiumResponse> activatePremium(
      @Valid @RequestBody ActivatePremiumRequest request
  ) {
    return ResponseEntity.ok(apiKeyService.activatePremium(request));
  }
}
