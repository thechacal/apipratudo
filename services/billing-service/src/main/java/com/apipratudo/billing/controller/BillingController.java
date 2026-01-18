package com.apipratudo.billing.controller;

import com.apipratudo.billing.dto.BillingChargeRequest;
import com.apipratudo.billing.dto.BillingChargeResponse;
import com.apipratudo.billing.dto.BillingChargeStatusResponse;
import com.apipratudo.billing.dto.BillingWebhookResponse;
import com.apipratudo.billing.error.ErrorResponse;
import com.apipratudo.billing.logging.TraceIdUtils;
import com.apipratudo.billing.service.BillingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/billing/pix")
@Validated
@Tag(name = "billing")
public class BillingController {

  private final BillingService billingService;

  public BillingController(BillingService billingService) {
    this.billingService = billingService;
  }

  @PostMapping("/charges")
  @SecurityRequirement(name = "ServiceToken")
  public ResponseEntity<BillingChargeResponse> createCharge(
      @Valid @RequestBody BillingChargeRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = TraceIdUtils.resolveTraceId(httpRequest);
    BillingChargeResponse response = billingService.createCharge(request, traceId);
    return ResponseEntity.status(201).body(response);
  }

  @GetMapping("/charges/{chargeId}")
  @SecurityRequirement(name = "ServiceToken")
  public ResponseEntity<BillingChargeStatusResponse> status(
      @PathVariable String chargeId,
      HttpServletRequest httpRequest
  ) {
    String traceId = TraceIdUtils.resolveTraceId(httpRequest);
    BillingChargeStatusResponse response = billingService.getChargeStatus(chargeId, traceId);
    return ResponseEntity.ok(response);
  }

  @PostMapping(value = "/webhook", consumes = MediaType.ALL_VALUE)
  public ResponseEntity<?> webhook(
      @RequestHeader(value = "X-Webhook-Secret", required = false) String webhookSecret,
      HttpServletRequest httpRequest,
      @RequestBody(required = false) byte[] body
  ) {
    String traceId = TraceIdUtils.resolveTraceId(httpRequest);
    String contentType = httpRequest.getContentType();
    String signature = billingService.resolveSignature(httpRequest);

    BillingService.WebhookResult result = billingService.handleWebhook(
        body == null ? new byte[0] : body,
        contentType,
        signature,
        webhookSecret,
        traceId
    );

    if (!result.ok()) {
      return ResponseEntity.status(result.status())
          .contentType(MediaType.APPLICATION_JSON)
          .body(new ErrorResponse("UNAUTHORIZED", result.message()));
    }

    BillingWebhookResponse response = new BillingWebhookResponse(true, result.mode(), result.warning());
    return ResponseEntity.ok(response);
  }
}
