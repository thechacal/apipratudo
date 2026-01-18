package com.apipratudo.gateway.keys.controller;

import com.apipratudo.gateway.error.ErrorResponse;
import com.apipratudo.gateway.keys.client.DeveloperPortalClient;
import com.apipratudo.gateway.keys.dto.KeyRequest;
import com.apipratudo.gateway.keys.dto.KeyUpgradeRequest;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/keys")
@Validated
@Tag(name = "keys")
public class KeysController {

  private static final Logger log = LoggerFactory.getLogger(KeysController.class);

  private final DeveloperPortalClient portalClient;

  public KeysController(DeveloperPortalClient portalClient) {
    this.portalClient = portalClient;
  }

  @PostMapping("/request")
  public ResponseEntity<?> requestKey(@Valid @RequestBody KeyRequest request) {
    String traceId = traceId();
    try {
      DeveloperPortalClient.PortalClientResult result = portalClient.requestKey(request, traceId);
      return mapPortalResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Portal request failed traceId={} reason={}", traceId, ex.getMessage());
      return portalUnavailable(traceId);
    }
  }

  @GetMapping("/status")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> status(@RequestHeader("X-Api-Key") String apiKey) {
    String traceId = traceId();
    try {
      DeveloperPortalClient.PortalClientResult result = portalClient.status(apiKey, traceId);
      return mapPortalResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Portal request failed traceId={} reason={}", traceId, ex.getMessage());
      return portalUnavailable(traceId);
    }
  }

  @PostMapping("/upgrade")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> upgrade(
      @RequestHeader("X-Api-Key") String apiKey,
      @Valid @RequestBody KeyUpgradeRequest request
  ) {
    String traceId = traceId();
    try {
      DeveloperPortalClient.PortalClientResult result = portalClient.upgrade(apiKey, request, traceId);
      return mapPortalResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Portal request failed traceId={} reason={}", traceId, ex.getMessage());
      return portalUnavailable(traceId);
    }
  }

  @GetMapping("/upgrade/{chargeId}")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> upgradeStatus(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String chargeId
  ) {
    String traceId = traceId();
    try {
      DeveloperPortalClient.PortalClientResult result = portalClient.upgradeStatus(apiKey, chargeId, traceId);
      return mapPortalResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Portal request failed traceId={} reason={}", traceId, ex.getMessage());
      return portalUnavailable(traceId);
    }
  }

  private String traceId() {
    String traceId = TraceIdUtils.currentTraceId();
    return traceId == null ? "-" : traceId;
  }

  private ResponseEntity<?> mapPortalResult(DeveloperPortalClient.PortalClientResult result, String traceId) {
    if (result.statusCode() >= 200 && result.statusCode() < 300) {
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    if (result.statusCode() == 401 || result.statusCode() == 403 || result.statusCode() >= 500) {
      log.warn("Portal response failed status={} traceId={}", result.statusCode(), traceId);
      return portalUnavailable(traceId);
    }
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  private ResponseEntity<ErrorResponse> portalUnavailable(String traceId) {
    ErrorResponse body = new ErrorResponse(
        "PORTAL_UNAVAILABLE",
        "Serviço de criação de chaves temporariamente indisponível",
        Collections.emptyList(),
        traceId
    );
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}
