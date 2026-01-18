package com.apipratudo.gateway.keys.controller;

import com.apipratudo.gateway.keys.client.DeveloperPortalClient;
import com.apipratudo.gateway.keys.dto.KeyRequest;
import com.apipratudo.gateway.keys.dto.KeyUpgradeRequest;
import com.apipratudo.gateway.logging.TraceIdUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

  private final DeveloperPortalClient portalClient;

  public KeysController(DeveloperPortalClient portalClient) {
    this.portalClient = portalClient;
  }

  @PostMapping("/request")
  public ResponseEntity<String> requestKey(@Valid @RequestBody KeyRequest request) {
    DeveloperPortalClient.PortalClientResult result = portalClient.requestKey(request, traceId());
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  @GetMapping("/status")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<String> status(@RequestHeader("X-Api-Key") String apiKey) {
    DeveloperPortalClient.PortalClientResult result = portalClient.status(apiKey, traceId());
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  @PostMapping("/upgrade")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<String> upgrade(
      @RequestHeader("X-Api-Key") String apiKey,
      @Valid @RequestBody KeyUpgradeRequest request
  ) {
    DeveloperPortalClient.PortalClientResult result = portalClient.upgrade(apiKey, request, traceId());
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  @GetMapping("/upgrade/{chargeId}")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<String> upgradeStatus(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String chargeId
  ) {
    DeveloperPortalClient.PortalClientResult result = portalClient.upgradeStatus(apiKey, chargeId, traceId());
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  private String traceId() {
    String traceId = TraceIdUtils.currentTraceId();
    return traceId == null ? "-" : traceId;
  }
}
