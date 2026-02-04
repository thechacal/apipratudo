package com.apipratudo.gateway.identity;

import com.apipratudo.gateway.error.ErrorResponse;
import com.apipratudo.gateway.idempotency.HashingUtils;
import com.apipratudo.gateway.identity.dto.DocumentValidateRequest;
import com.apipratudo.gateway.identity.dto.VerificationRequest;
import com.apipratudo.gateway.logging.TraceIdUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Validated
@Tag(name = "documentos")
public class IdentityVerifyController {

  private static final Logger log = LoggerFactory.getLogger(IdentityVerifyController.class);
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private final IdentityVerifyClient client;

  public IdentityVerifyController(IdentityVerifyClient client) {
    this.client = client;
  }

  @PostMapping("/documentos/validar")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> validarDocumento(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody DocumentValidateRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    try {
      IdentityVerifyClient.IdentityVerifyClientResult result = client.validarDocumento(
          tenantId(apiKey), request, requestId(httpRequest), idempotencyKey);
      return mapResult(result);
    } catch (Exception ex) {
      log.warn("Identity verify unavailable traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/cnpj/{cnpj}/status")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> cnpjStatus(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String cnpj,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    try {
      IdentityVerifyClient.IdentityVerifyClientResult result = client.cnpjStatus(
          tenantId(apiKey), cnpj, requestId(httpRequest));
      return mapResult(result);
    } catch (Exception ex) {
      log.warn("Identity verify unavailable traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/verificacoes")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> verificacoes(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody VerificationRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    try {
      IdentityVerifyClient.IdentityVerifyClientResult result = client.verificacoes(
          tenantId(apiKey), request, requestId(httpRequest), idempotencyKey);
      return mapResult(result);
    } catch (Exception ex) {
      log.warn("Identity verify unavailable traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  private ResponseEntity<?> mapResult(IdentityVerifyClient.IdentityVerifyClientResult result) {
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  private ResponseEntity<ErrorResponse> serviceUnavailable(String traceId) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse("IDENTITY_UNAVAILABLE", "Servico de verificacao temporariamente indisponivel",
            Collections.emptyList(), traceId));
  }

  private String tenantId(String apiKey) {
    return HashingUtils.sha256Hex(apiKey);
  }

  private String traceId(HttpServletRequest request) {
    return TraceIdUtils.resolveTraceId(request);
  }

  private String requestId(HttpServletRequest request) {
    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (StringUtils.hasText(requestId)) {
      return requestId.trim();
    }
    return UUID.randomUUID().toString();
  }
}
