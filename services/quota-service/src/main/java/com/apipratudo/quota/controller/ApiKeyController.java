package com.apipratudo.quota.controller;

import com.apipratudo.quota.dto.ApiKeyCreateRequest;
import com.apipratudo.quota.dto.ApiKeyCreateResponse;
import com.apipratudo.quota.dto.ApiKeyResponse;
import com.apipratudo.quota.dto.ApiKeyRotateResponse;
import com.apipratudo.quota.dto.ApiKeyStatusUpdateRequest;
import com.apipratudo.quota.service.ApiKeyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api-keys")
@Validated
@Tag(name = "api-keys")
@SecurityRequirement(name = "AdminToken")
public class ApiKeyController {

  private final ApiKeyService apiKeyService;

  public ApiKeyController(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @PostMapping
  public ResponseEntity<ApiKeyCreateResponse> create(@Valid @RequestBody ApiKeyCreateRequest request) {
    ApiKeyCreateResponse response = apiKeyService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{id}")
  public ApiKeyResponse get(@PathVariable String id) {
    return apiKeyService.get(id);
  }

  @PostMapping("/{id}/rotate")
  public ApiKeyRotateResponse rotate(@PathVariable String id) {
    return apiKeyService.rotate(id);
  }

  @PatchMapping("/{id}/status")
  public ApiKeyResponse updateStatus(
      @PathVariable String id,
      @Valid @RequestBody ApiKeyStatusUpdateRequest request
  ) {
    return apiKeyService.updateStatus(id, request.status());
  }
}
