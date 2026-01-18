package com.apipratudo.portal.controller;

import com.apipratudo.portal.client.BillingClient;
import com.apipratudo.portal.client.QuotaClient;
import com.apipratudo.portal.dto.KeyRequest;
import com.apipratudo.portal.dto.KeyRequestResponse;
import com.apipratudo.portal.dto.KeyUpgradeRequest;
import com.apipratudo.portal.logging.TraceIdUtils;
import com.apipratudo.portal.service.KeyService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/keys")
@Validated
@Tag(name = "keys")
public class KeysController {

  private final KeyService keyService;

  public KeysController(KeyService keyService) {
    this.keyService = keyService;
  }

  @PostMapping("/request")
  public ResponseEntity<KeyRequestResponse> requestKey(
      @Valid @RequestBody KeyRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = TraceIdUtils.resolveTraceId(httpRequest);
    String clientIp = resolveIp(httpRequest);
    KeyRequestResponse response = keyService.requestKey(request, clientIp, traceId);
    return ResponseEntity.status(201).body(response);
  }

  @GetMapping("/status")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<String> status(
      @RequestHeader("X-Api-Key") String apiKey,
      HttpServletRequest httpRequest
  ) {
    String traceId = TraceIdUtils.resolveTraceId(httpRequest);
    QuotaClient.ClientResult result = keyService.status(apiKey, traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  @PostMapping("/upgrade")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<String> upgrade(
      @RequestHeader("X-Api-Key") String apiKey,
      @Valid @RequestBody KeyUpgradeRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = TraceIdUtils.resolveTraceId(httpRequest);
    BillingClient.ClientResult result = keyService.upgrade(apiKey, request, traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  @GetMapping("/upgrade/{chargeId}")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<String> upgradeStatus(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String chargeId,
      HttpServletRequest httpRequest
  ) {
    String traceId = TraceIdUtils.resolveTraceId(httpRequest);
    BillingClient.ClientResult result = keyService.upgradeStatus(chargeId, traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  private String resolveIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      String[] parts = forwarded.split(",");
      if (parts.length > 0) {
        return parts[0].trim();
      }
    }
    return request.getRemoteAddr();
  }
}
