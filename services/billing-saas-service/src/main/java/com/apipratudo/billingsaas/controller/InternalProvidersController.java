package com.apipratudo.billingsaas.controller;

import com.apipratudo.billingsaas.dto.PagbankConnectRequest;
import com.apipratudo.billingsaas.dto.PagbankStatusResponse;
import com.apipratudo.billingsaas.service.PagbankProviderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/providers")
@Validated
@Tag(name = "providers")
public class InternalProvidersController {

  private final PagbankProviderService pagbankProviderService;

  public InternalProvidersController(PagbankProviderService pagbankProviderService) {
    this.pagbankProviderService = pagbankProviderService;
  }

  @PostMapping("/pagbank/connect")
  @SecurityRequirement(name = "ServiceToken")
  public ResponseEntity<PagbankStatusResponse> connectPagbank(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @Valid @RequestBody PagbankConnectRequest request
  ) {
    pagbankProviderService.connect(tenantId, request);
    return ResponseEntity.ok(pagbankProviderService.status(tenantId));
  }

  @GetMapping("/pagbank/status")
  @SecurityRequirement(name = "ServiceToken")
  public ResponseEntity<PagbankStatusResponse> pagbankStatus(
      @RequestHeader("X-Tenant-Id") String tenantId
  ) {
    return ResponseEntity.ok(pagbankProviderService.status(tenantId));
  }

  @DeleteMapping("/pagbank/disconnect")
  @SecurityRequirement(name = "ServiceToken")
  public ResponseEntity<Void> disconnectPagbank(
      @RequestHeader("X-Tenant-Id") String tenantId
  ) {
    pagbankProviderService.disconnect(tenantId);
    return ResponseEntity.noContent().build();
  }
}
