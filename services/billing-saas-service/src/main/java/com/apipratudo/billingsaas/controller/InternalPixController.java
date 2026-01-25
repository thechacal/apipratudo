package com.apipratudo.billingsaas.controller;

import com.apipratudo.billingsaas.config.WebhookProperties;
import com.apipratudo.billingsaas.dto.PixGenerateRequest;
import com.apipratudo.billingsaas.dto.PixGenerateResponse;
import com.apipratudo.billingsaas.error.UnauthorizedException;
import com.apipratudo.billingsaas.idempotency.IdempotencyResponse;
import com.apipratudo.billingsaas.idempotency.IdempotencyResult;
import com.apipratudo.billingsaas.idempotency.IdempotencyService;
import com.apipratudo.billingsaas.service.ChargeService;
import com.apipratudo.billingsaas.service.PixWebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/pix")
@Validated
@Tag(name = "pix")
public class InternalPixController {

  private final ChargeService chargeService;
  private final IdempotencyService idempotencyService;
  private final WebhookProperties webhookProperties;
  private final ObjectMapper objectMapper;
  private final PixWebhookService pixWebhookService;

  public InternalPixController(
      ChargeService chargeService,
      IdempotencyService idempotencyService,
      WebhookProperties webhookProperties,
      ObjectMapper objectMapper,
      PixWebhookService pixWebhookService
  ) {
    this.chargeService = chargeService;
    this.idempotencyService = idempotencyService;
    this.webhookProperties = webhookProperties;
    this.objectMapper = objectMapper;
    this.pixWebhookService = pixWebhookService;
  }

  @PostMapping("/gerar")
  @SecurityRequirement(name = "ServiceToken")
  public ResponseEntity<String> generate(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody PixGenerateRequest request,
      HttpServletRequest httpRequest
  ) {
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        request,
        transaction -> {
          PixGenerateResponse response = chargeService.generatePix(
              tenantId,
              request.getChargeId(),
              request.getExpiresInSeconds(),
              transaction
          );
          return new IdempotencyResponse(200, toJson(response), null);
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.responseBodyJson());
  }

  @PostMapping("/webhook")
  @SecurityRequirement(name = "WebhookSecret")
  public ResponseEntity<Map<String, Object>> webhook(
      @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
      HttpServletRequest httpRequest,
      @RequestBody(required = false) byte[] body
  ) {
    validateWebhookSecret(secret);
    String signature = resolveSignature(httpRequest);
    PixWebhookService.WebhookResult result = pixWebhookService.handle(
        body == null ? new byte[0] : body,
        httpRequest.getContentType(),
        signature
    );
    if (!result.ok()) {
      throw new UnauthorizedException(result.message());
    }
    Map<String, Object> response = new java.util.HashMap<>();
    response.put("ok", true);
    if (result.mode() != null) {
      response.put("mode", result.mode());
    }
    if (result.warning() != null) {
      response.put("warning", result.warning());
    }
    return ResponseEntity.ok(response);
  }

  private void validateWebhookSecret(String provided) {
    String expected = webhookProperties.getSecret();
    if (!StringUtils.hasText(expected) || !expected.equals(provided)) {
      throw new UnauthorizedException("Missing or invalid X-Webhook-Secret");
    }
  }

  private String resolveSignature(HttpServletRequest request) {
    String signature = request.getHeader("x-authenticity-token");
    if (!StringUtils.hasText(signature)) {
      signature = request.getHeader("X-Authenticity-Token");
    }
    return signature == null ? "" : signature.trim();
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize response", e);
    }
  }
}
