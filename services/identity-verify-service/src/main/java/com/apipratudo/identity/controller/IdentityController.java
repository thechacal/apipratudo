package com.apipratudo.identity.controller;

import com.apipratudo.identity.dto.CnpjStatusResponse;
import com.apipratudo.identity.dto.DocumentValidateRequest;
import com.apipratudo.identity.dto.DocumentValidateResponse;
import com.apipratudo.identity.dto.VerificationRequest;
import com.apipratudo.identity.dto.VerificationResponse;
import com.apipratudo.identity.service.IdempotencyService;
import com.apipratudo.identity.service.IdentityService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
@Validated
public class IdentityController {

  private final IdentityService identityService;
  private final IdempotencyService idempotencyService;

  public IdentityController(IdentityService identityService, IdempotencyService idempotencyService) {
    this.identityService = identityService;
    this.idempotencyService = idempotencyService;
  }

  @PostMapping("/documentos/validar")
  public ResponseEntity<?> validarDocumento(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody DocumentValidateRequest request
  ) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyService.IdempotencyResult result = idempotencyService.execute(
          tenantId, "POST:/internal/v1/documentos/validar", idempotencyKey,
          () -> identityService.validarDocumento(request)
      );
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    DocumentValidateResponse response = identityService.validarDocumento(request);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/cnpj/{cnpj}/status")
  public ResponseEntity<CnpjStatusResponse> cnpjStatus(
      @PathVariable String cnpj
  ) {
    return ResponseEntity.ok(identityService.consultarCnpj(cnpj));
  }

  @PostMapping("/verificacoes")
  public ResponseEntity<?> verificacoes(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody VerificationRequest request
  ) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyService.IdempotencyResult result = idempotencyService.execute(
          tenantId, "POST:/internal/v1/verificacoes", idempotencyKey,
          () -> identityService.verificar(request)
      );
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    VerificationResponse response = identityService.verificar(request);
    return ResponseEntity.ok(response);
  }
}
