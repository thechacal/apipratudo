package com.apipratudo.quota.controller;

import com.apipratudo.quota.dto.CreateFreeKeyRequest;
import com.apipratudo.quota.dto.CreateFreeKeyResponse;
import com.apipratudo.quota.service.ApiKeyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/internal/keys")
@Validated
@Tag(name = "internal-keys")
@SecurityRequirement(name = "PortalToken")
@SecurityRequirement(name = "InternalToken")
public class InternalKeysController {

  private final ApiKeyService apiKeyService;

  public InternalKeysController(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @PostMapping("/create-free")
  public ResponseEntity<CreateFreeKeyResponse> createFree(@Valid @RequestBody CreateFreeKeyRequest request) {
    CreateFreeKeyResponse response = apiKeyService.createFreeKey(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
