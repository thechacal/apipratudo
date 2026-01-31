package com.apipratudo.gateway.helpdesk;

import com.apipratudo.gateway.error.ErrorResponse;
import com.apipratudo.gateway.helpdesk.dto.AssignRequest;
import com.apipratudo.gateway.helpdesk.dto.MessageCreateRequest;
import com.apipratudo.gateway.helpdesk.dto.StatusUpdateRequest;
import com.apipratudo.gateway.helpdesk.dto.TemplateCreateRequest;
import com.apipratudo.gateway.helpdesk.dto.TicketCreateRequest;
import com.apipratudo.gateway.idempotency.HashingUtils;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Validated
@Tag(name = "helpdesk")
public class HelpdeskController {

  private static final Logger log = LoggerFactory.getLogger(HelpdeskController.class);
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private final HelpdeskClient helpdeskClient;

  public HelpdeskController(HelpdeskClient helpdeskClient) {
    this.helpdeskClient = helpdeskClient;
  }

  @GetMapping("/tickets")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> listTickets(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestParam(name = "status", required = false) String status,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    String requestId = requestId(request);
    try {
      HelpdeskClient.HelpdeskClientResult result = helpdeskClient.listTickets(tenantId(apiKey), requestId, status);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Helpdesk request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/tickets")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> createTicket(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody TicketCreateRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      HelpdeskClient.HelpdeskClientResult result = helpdeskClient.createTicket(
          tenantId(apiKey), request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Helpdesk request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/tickets/{ticketId}")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> getTicket(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String ticketId,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    String requestId = requestId(request);
    try {
      HelpdeskClient.HelpdeskClientResult result = helpdeskClient.getTicket(tenantId(apiKey), ticketId, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Helpdesk request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/tickets/{ticketId}/mensagens")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> listMessages(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String ticketId,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    String requestId = requestId(request);
    try {
      HelpdeskClient.HelpdeskClientResult result = helpdeskClient.listMessages(tenantId(apiKey), ticketId, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Helpdesk request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/tickets/{ticketId}/mensagens")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> createMessage(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @PathVariable String ticketId,
      @Valid @RequestBody MessageCreateRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      HelpdeskClient.HelpdeskClientResult result = helpdeskClient.createMessage(
          tenantId(apiKey), ticketId, request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Helpdesk request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/tickets/{ticketId}/atribuir")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> assignTicket(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @PathVariable String ticketId,
      @Valid @RequestBody AssignRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      HelpdeskClient.HelpdeskClientResult result = helpdeskClient.assignTicket(
          tenantId(apiKey), ticketId, request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Helpdesk request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/tickets/{ticketId}/status")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> updateStatus(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @PathVariable String ticketId,
      @Valid @RequestBody StatusUpdateRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      HelpdeskClient.HelpdeskClientResult result = helpdeskClient.updateStatus(
          tenantId(apiKey), ticketId, request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Helpdesk request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/templates")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> listTemplates(
      @RequestHeader("X-Api-Key") String apiKey,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    String requestId = requestId(request);
    try {
      HelpdeskClient.HelpdeskClientResult result = helpdeskClient.listTemplates(tenantId(apiKey), requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Helpdesk request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/templates")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> createTemplate(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody TemplateCreateRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      HelpdeskClient.HelpdeskClientResult result = helpdeskClient.createTemplate(
          tenantId(apiKey), request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Helpdesk request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/webhook/whatsapp")
  public ResponseEntity<?> verifyWebhook(
      @RequestParam(name = "hub.mode", required = false) String mode,
      @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
      @RequestParam(name = "hub.challenge", required = false) String challenge
  ) {
    try {
      HelpdeskClient.HelpdeskClientResult result = helpdeskClient.webhookVerify(mode, verifyToken, challenge);
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.TEXT_PLAIN)
          .body(result.body());
    } catch (Exception ex) {
      log.warn("Helpdesk webhook verify failed reason={}", ex.getMessage());
      return serviceUnavailable("-");
    }
  }

  @PostMapping("/webhook/whatsapp")
  public ResponseEntity<?> receiveWebhook(
      HttpServletRequest request,
      @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature
  ) {
    try {
      byte[] rawBodyBytes = request.getInputStream().readAllBytes();
      String contentType = request.getContentType();
      HelpdeskClient.HelpdeskClientResult result = helpdeskClient.webhookReceive(rawBodyBytes, contentType, signature);
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    } catch (Exception ex) {
      log.warn("Helpdesk webhook receive failed reason={}", ex.getMessage());
      return serviceUnavailable("-");
    }
  }

  private ResponseEntity<?> mapResult(HelpdeskClient.HelpdeskClientResult result, String traceId) {
    if (result.statusCode() >= 200 && result.statusCode() < 300) {
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  private ResponseEntity<ErrorResponse> serviceUnavailable(String traceId) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse("HELPDESK_UNAVAILABLE", "Helpdesk service unavailable", Collections.emptyList(),
            traceId));
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
