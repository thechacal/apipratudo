package com.apipratudo.gateway.scheduling;

import com.apipratudo.gateway.error.ErrorResponse;
import com.apipratudo.gateway.idempotency.HashingUtils;
import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.scheduling.dto.AgendaCreateRequest;
import com.apipratudo.gateway.scheduling.dto.AgendaCreditsUpgradeRequest;
import com.apipratudo.gateway.scheduling.dto.AgendaUpdateRequest;
import com.apipratudo.gateway.scheduling.dto.AttendedRequest;
import com.apipratudo.gateway.scheduling.dto.CancelRequest;
import com.apipratudo.gateway.scheduling.dto.ConfirmRequest;
import com.apipratudo.gateway.scheduling.dto.FineWaiveRequest;
import com.apipratudo.gateway.scheduling.dto.NotifyRequest;
import com.apipratudo.gateway.scheduling.dto.ReservationRequest;
import com.apipratudo.gateway.scheduling.dto.ServiceCreateRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
@Tag(name = "agendamento")
public class SchedulingController {

  private static final Logger log = LoggerFactory.getLogger(SchedulingController.class);
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private final SchedulingClient schedulingClient;

  public SchedulingController(SchedulingClient schedulingClient) {
    this.schedulingClient = schedulingClient;
  }

  @GetMapping("/servicos")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> listServices(
      @RequestHeader("X-Api-Key") String apiKey,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    String requestId = requestId(request);
    try {
      SchedulingClient.SchedulingClientResult result =
          schedulingClient.listServices(tenantId(apiKey), requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/agendas")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> listAgendas(
      @RequestHeader("X-Api-Key") String apiKey,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    String requestId = requestId(request);
    try {
      SchedulingClient.SchedulingClientResult result =
          schedulingClient.listAgendas(tenantId(apiKey), requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/agendas")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> createAgenda(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody AgendaCreateRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.createAgenda(
          tenantId(apiKey), request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/agendas/{id}")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> getAgenda(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String id,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    String requestId = requestId(request);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.getAgenda(
          tenantId(apiKey), id, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PatchMapping("/agendas/{id}")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> updateAgenda(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody AgendaUpdateRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.updateAgenda(
          tenantId(apiKey), id, request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/servicos")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> createService(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ServiceCreateRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.createService(
          tenantId(apiKey), request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/agenda")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> agenda(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(defaultValue = "main") String agendaId,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    String requestId = requestId(request);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.agenda(
          tenantId(apiKey), from, to, agendaId, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/slots-disponiveis")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> slotsDisponiveis(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestParam String serviceId,
      @RequestParam String date,
      @RequestParam(defaultValue = "main") String agendaId,
      @RequestParam(required = false) String zoneId,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    String requestId = requestId(request);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.slotsDisponiveis(
          tenantId(apiKey), serviceId, date, agendaId, zoneId, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/reservar")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> reservar(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ReservationRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.reservar(
          tenantId(apiKey), request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/atendido")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> attended(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody AttendedRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.attended(
          tenantId(apiKey), request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/confirmar")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> confirmar(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ConfirmRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.confirmar(
          tenantId(apiKey), request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/cancelar")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> cancelar(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CancelRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.cancelar(
          tenantId(apiKey), request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/notificar")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> notificar(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody NotifyRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.notificar(
          tenantId(apiKey), request, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/agendas/{id}/creditos/upgrade")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> upgradeAgendaCredits(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody(required = false) AgendaCreditsUpgradeRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    AgendaCreditsUpgradeRequest payload = request == null ? new AgendaCreditsUpgradeRequest(null) : request;
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.upgradeAgendaCredits(
          tenantId(apiKey), id, payload, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/agendas/{id}/creditos/status/{chargeId}")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> creditsStatus(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String id,
      @PathVariable String chargeId,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    String requestId = requestId(request);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.creditsStatus(
          tenantId(apiKey), id, chargeId, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @GetMapping("/multas")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> listFines(
      @RequestHeader("X-Api-Key") String apiKey,
      @RequestParam(required = false) String agendaId,
      @RequestParam(required = false) String status,
      HttpServletRequest request
  ) {
    String traceId = traceId(request);
    String requestId = requestId(request);
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.listFines(
          tenantId(apiKey), agendaId, status, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  @PostMapping("/multas/{id}/waive")
  @SecurityRequirement(name = "ApiKeyAuth")
  public ResponseEntity<?> waiveFine(
      @RequestHeader("X-Api-Key") String apiKey,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody(required = false) FineWaiveRequest request,
      HttpServletRequest httpRequest
  ) {
    String traceId = traceId(httpRequest);
    String requestId = requestId(httpRequest);
    FineWaiveRequest payload = request == null ? new FineWaiveRequest(null) : request;
    try {
      SchedulingClient.SchedulingClientResult result = schedulingClient.waiveFine(
          tenantId(apiKey), id, payload, idempotencyKey, requestId);
      return mapResult(result, traceId);
    } catch (Exception ex) {
      log.warn("Scheduling request failed traceId={} reason={}", traceId, ex.getMessage());
      return serviceUnavailable(traceId);
    }
  }

  private String tenantId(String apiKey) {
    return HashingUtils.sha256Hex(apiKey);
  }

  private String traceId(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return traceId == null ? "-" : traceId;
  }

  private String requestId(HttpServletRequest request) {
    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (StringUtils.hasText(requestId)) {
      return requestId.trim();
    }
    String traceId = TraceIdUtils.resolveTraceId(request);
    if (StringUtils.hasText(traceId)) {
      return traceId.trim();
    }
    return UUID.randomUUID().toString();
  }

  private ResponseEntity<?> mapResult(SchedulingClient.SchedulingClientResult result, String traceId) {
    if (result.statusCode() >= 200 && result.statusCode() < 300) {
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    if (result.statusCode() == 401 || result.statusCode() == 403 || result.statusCode() >= 500) {
      log.warn("Scheduling response failed status={} traceId={}", result.statusCode(), traceId);
      return serviceUnavailable(traceId);
    }
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  private ResponseEntity<ErrorResponse> serviceUnavailable(String traceId) {
    ErrorResponse body = new ErrorResponse(
        "SCHEDULING_UNAVAILABLE",
        "Servico de agendamento temporariamente indisponivel",
        Collections.emptyList(),
        traceId
    );
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}
