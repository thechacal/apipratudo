package com.apipratudo.billingsaas.controller;

import com.apipratudo.billingsaas.dto.ChargeCreateRequest;
import com.apipratudo.billingsaas.dto.ChargeResponse;
import com.apipratudo.billingsaas.dto.ChargeStatusResponse;
import com.apipratudo.billingsaas.idempotency.IdempotencyResponse;
import com.apipratudo.billingsaas.idempotency.IdempotencyResult;
import com.apipratudo.billingsaas.idempotency.IdempotencyService;
import com.apipratudo.billingsaas.model.Charge;
import com.apipratudo.billingsaas.service.ChargeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/internal/cobrancas")
@Validated
@Tag(name = "cobrancas")
@SecurityRequirement(name = "ServiceToken")
public class InternalChargesController {

  private final ChargeService chargeService;
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;

  public InternalChargesController(
      ChargeService chargeService,
      IdempotencyService idempotencyService,
      ObjectMapper objectMapper
  ) {
    this.chargeService = chargeService;
    this.idempotencyService = idempotencyService;
    this.objectMapper = objectMapper;
  }

  @PostMapping
  public ResponseEntity<String> create(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ChargeCreateRequest request,
      HttpServletRequest httpRequest
  ) {
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        request,
        transaction -> {
          Charge charge = chargeService.create(tenantId, request, transaction);
          ChargeResponse response = chargeService.toResponse(charge);
          return new IdempotencyResponse(
              HttpStatus.CREATED.value(),
              toJson(response),
              null
          );
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.responseBodyJson());
  }

  @GetMapping("/{id}")
  public ResponseEntity<ChargeResponse> get(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @PathVariable String id
  ) {
    Charge charge = chargeService.get(tenantId, id);
    return ResponseEntity.ok(chargeService.toResponse(charge));
  }

  @GetMapping("/{id}/status")
  public ResponseEntity<ChargeStatusResponse> status(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @PathVariable String id
  ) {
    Charge charge = chargeService.get(tenantId, id);
    return ResponseEntity.ok(chargeService.toStatusResponse(charge));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize response", e);
    }
  }
}
