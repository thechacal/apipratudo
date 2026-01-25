package com.apipratudo.gateway.billingsaas;

import com.apipratudo.gateway.billingsaas.dto.ChargeCreateRequest;
import com.apipratudo.gateway.billingsaas.dto.CustomerCreateRequest;
import com.apipratudo.gateway.billingsaas.dto.PixGenerateRequest;
import com.apipratudo.gateway.error.ErrorResponse;
import com.apipratudo.gateway.idempotency.HashingUtils;
import com.apipratudo.gateway.logging.TraceIdUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Validated
@Tag(name = "billing-saas")
public class BillingSaasController {

  private static final Logger log = LoggerFactory.getLogger(BillingSaasController.class);

  private final BillingSaasClient billingSaasClient;

  public BillingSaasController(BillingSaasClient billingSaasClient) {
    this.billingSaasClient = billingSaasClient;
  }

  @PostMapping("/clientes")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> createCustomer(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CustomerCreateRequest request
  ) {
    String traceId = traceId();
    try {
      BillingSaasClient.BillingSaasClientResult result = billingSaasClient.createCustomer(
          tenantId(apiKey), request, idempotencyKey, traceId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Billing SaaS request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/clientes/{id}")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> getCustomer(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String id
  ) {
    String traceId = traceId();
    try {
      BillingSaasClient.BillingSaasClientResult result = billingSaasClient.getCustomer(
          tenantId(apiKey), id, traceId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Billing SaaS request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/cobrancas")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> createCharge(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ChargeCreateRequest request
  ) {
    String traceId = traceId();
    try {
      BillingSaasClient.BillingSaasClientResult result = billingSaasClient.createCharge(
          tenantId(apiKey), request, idempotencyKey, traceId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Billing SaaS request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/cobrancas/{id}")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> getCharge(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String id
  ) {
    String traceId = traceId();
    try {
      BillingSaasClient.BillingSaasClientResult result = billingSaasClient.getCharge(
          tenantId(apiKey), id, traceId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Billing SaaS request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/cobrancas/{id}/status")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> getChargeStatus(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String id
  ) {
    String traceId = traceId();
    try {
      BillingSaasClient.BillingSaasClientResult result = billingSaasClient.getChargeStatus(
          tenantId(apiKey), id, traceId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Billing SaaS request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/pix/gerar")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> generatePix(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody PixGenerateRequest request
  ) {
    String traceId = traceId();
    try {
      BillingSaasClient.BillingSaasClientResult result = billingSaasClient.generatePix(
          tenantId(apiKey), request, idempotencyKey, traceId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Billing SaaS request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/pix/webhook")
  public ResponseEntity<?> pixWebhook(
      @RequestHeader(value = "X-Webhook-Secret", required = false) String webhookSecret,
      @RequestBody String body
  ) {
    String traceId = traceId();
    try {
      BillingSaasClient.BillingSaasClientResult result = billingSaasClient.webhook(body, webhookSecret, traceId);
      return mapWebhookResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Billing SaaS webhook failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/relatorios")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> report(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to
  ) {
    String traceId = traceId();
    try {
      BillingSaasClient.BillingSaasClientResult result = billingSaasClient.report(
          tenantId(apiKey), from, to, traceId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Billing SaaS request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  private String traceId() {
    String traceId = TraceIdUtils.currentTraceId();
    return traceId == null ? "-" : traceId;
  }

  private String tenantId(String apiKey) {
    return HashingUtils.sha256Hex(apiKey);
  }

  private ResponseEntity<?> mapResult(BillingSaasClient.BillingSaasClientResult result, String traceId) {
    if (result.statusCode() >= 200 && result.statusCode() < 300) {
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    if (result.statusCode() == 401 || result.statusCode() == 403 || result.statusCode() >= 500) {
      log.warn("Billing SaaS response failed status={} traceId={}", result.statusCode(), traceId);
      return serviceUnavailable(traceId);
    }
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  private ResponseEntity<?> mapWebhookResult(BillingSaasClient.BillingSaasClientResult result, String traceId) {
    if (result.statusCode() >= 200 && result.statusCode() < 300) {
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    if (result.statusCode() >= 500) {
      log.warn("Billing SaaS webhook failed status={} traceId={}", result.statusCode(), traceId);
      return serviceUnavailable(traceId);
    }
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  private ResponseEntity<ErrorResponse> serviceUnavailable(String traceId) {
    ErrorResponse body = new ErrorResponse(
        "BILLING_SAAS_UNAVAILABLE",
        "Servico de cobranca temporariamente indisponivel",
        Collections.emptyList(),
        traceId
    );
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}
