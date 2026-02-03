package com.apipratudo.gateway.reconciliation;

import com.apipratudo.gateway.error.ErrorResponse;
import com.apipratudo.gateway.idempotency.HashingUtils;
import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.reconciliation.dto.MatchRequest;
import com.apipratudo.gateway.reconciliation.dto.PaymentWebhookRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1")
@Validated
@Tag(name = "reconciliation")
public class ReconciliationController {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private final ReconciliationClient client;

  public ReconciliationController(ReconciliationClient client) {
    this.client = client;
  }

  @PostMapping(value = "/importar-extrato", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> importarExtrato(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "accountId", required = false) String accountId,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    try {
      ReconciliationClient.ReconciliationClientResult result = client.importarExtrato(
          tenantId(apiKey), file.getBytes(), file.getOriginalFilename(), accountId, requestId(request), idempotencyKey);
      return mapResult(result);
    } catch (Exception ex) {
      log.warn("Reconciliation request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/match")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> match(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @org.springframework.web.bind.annotation.RequestBody MatchRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    try {
      ReconciliationClient.ReconciliationClientResult result = client.match(
          tenantId(apiKey), request, requestId(httpRequest), idempotencyKey);
      return mapResult(result);
    } catch (Exception ex) {
      log.warn("Reconciliation request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/conciliado")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> conciliado(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestParam String importId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    try {
      ReconciliationClient.ReconciliationClientResult result = client.conciliado(
          tenantId(apiKey), importId, page, size, requestId(request));
      return mapResult(result);
    } catch (Exception ex) {
      log.warn("Reconciliation request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/pendencias")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> pendencias(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestParam String importId,
      @RequestParam(required = false) String tipo,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    try {
      ReconciliationClient.ReconciliationClientResult result = client.pendencias(
          tenantId(apiKey), importId, tipo, page, size, requestId(request));
      return mapResult(result);
    } catch (Exception ex) {
      log.warn("Reconciliation request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/webhook/pagamento")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> webhookPagamento(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @org.springframework.web.bind.annotation.RequestBody PaymentWebhookRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    try {
      ReconciliationClient.ReconciliationClientResult result = client.webhookPagamento(
          tenantId(apiKey), request, requestId(httpRequest), idempotencyKey);
      return mapResult(result);
    } catch (Exception ex) {
      log.warn("Reconciliation request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  private ResponseEntity<?> mapResult(ReconciliationClient.ReconciliationClientResult result) {
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  private ResponseEntity<ErrorResponse> serviceUnavailable(String traceId) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse("RECONCILIATION_UNAVAILABLE", "Servico de conciliacao temporariamente indisponivel",
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
