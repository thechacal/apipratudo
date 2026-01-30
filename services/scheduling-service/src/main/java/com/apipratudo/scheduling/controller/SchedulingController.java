package com.apipratudo.scheduling.controller;

import com.apipratudo.scheduling.dto.AgendaCreateRequest;
import com.apipratudo.scheduling.dto.AgendaCreditsStatusResponse;
import com.apipratudo.scheduling.dto.AgendaCreditsUpgradeRequest;
import com.apipratudo.scheduling.dto.AgendaCreditsUpgradeResponse;
import com.apipratudo.scheduling.dto.AgendaInfoResponse;
import com.apipratudo.scheduling.dto.AgendaResponse;
import com.apipratudo.scheduling.dto.AgendaUpdateRequest;
import com.apipratudo.scheduling.dto.AttendedRequest;
import com.apipratudo.scheduling.dto.AttendedResponse;
import com.apipratudo.scheduling.dto.CancelRequest;
import com.apipratudo.scheduling.dto.CancelResponse;
import com.apipratudo.scheduling.dto.ConfirmRequest;
import com.apipratudo.scheduling.dto.ConfirmResponse;
import com.apipratudo.scheduling.dto.FineResponse;
import com.apipratudo.scheduling.dto.FineWaiveRequest;
import com.apipratudo.scheduling.dto.NotifyRequest;
import com.apipratudo.scheduling.dto.NotifyResponse;
import com.apipratudo.scheduling.dto.ReservationRequest;
import com.apipratudo.scheduling.dto.ReservationResponse;
import com.apipratudo.scheduling.dto.ServiceCreateRequest;
import com.apipratudo.scheduling.dto.ServiceResponse;
import com.apipratudo.scheduling.dto.SlotsResponse;
import com.apipratudo.scheduling.idempotency.IdempotencyResponse;
import com.apipratudo.scheduling.idempotency.IdempotencyResult;
import com.apipratudo.scheduling.idempotency.IdempotencyService;
import com.apipratudo.scheduling.logging.TraceIdUtils;
import com.apipratudo.scheduling.model.Agenda;
import com.apipratudo.scheduling.model.FineStatus;
import com.apipratudo.scheduling.model.ServiceDef;
import com.apipratudo.scheduling.service.SchedulingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
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
public class SchedulingController {

  private final SchedulingService schedulingService;
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;

  public SchedulingController(
      SchedulingService schedulingService,
      IdempotencyService idempotencyService,
      ObjectMapper objectMapper
  ) {
    this.schedulingService = schedulingService;
    this.idempotencyService = idempotencyService;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/servicos")
  public ResponseEntity<List<ServiceResponse>> listServices(@RequestHeader("X-Tenant-Id") String tenantId,
                                                            HttpServletRequest request) {
    List<ServiceResponse> response = schedulingService.listServices(tenantId).stream()
        .map(schedulingService::toResponse)
        .toList();
    return ResponseEntity.ok()
        .headers(responseHeaders(request))
        .body(response);
  }

  @GetMapping("/agendas")
  public ResponseEntity<List<AgendaInfoResponse>> listAgendas(
      @RequestHeader("X-Tenant-Id") String tenantId,
      HttpServletRequest request
  ) {
    List<AgendaInfoResponse> response = schedulingService.listAgendas(tenantId).stream()
        .map(agenda -> new AgendaInfoResponse(
            agenda.id(),
            agenda.name(),
            agenda.timezone(),
            agenda.workingHoursStart(),
            agenda.workingHoursEnd(),
            agenda.slotStepMin(),
            agenda.noShowFeeCents(),
            agenda.active(),
            agenda.createdAt(),
            agenda.updatedAt(),
            schedulingService.creditsStatus(tenantId, agenda.id(), null).creditsBalance()
        ))
        .toList();
    return ResponseEntity.ok()
        .headers(responseHeaders(request))
        .body(response);
  }

  @PostMapping("/agendas")
  public ResponseEntity<String> createAgenda(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody AgendaCreateRequest request,
      HttpServletRequest httpRequest
  ) {
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        request,
        transaction -> {
          Agenda created = schedulingService.createAgenda(tenantId, request, transaction);
          AgendaInfoResponse response = new AgendaInfoResponse(
              created.id(),
              created.name(),
              created.timezone(),
              created.workingHoursStart(),
              created.workingHoursEnd(),
              created.slotStepMin(),
              created.noShowFeeCents(),
              created.active(),
              created.createdAt(),
              created.updatedAt(),
              schedulingService.creditsStatus(tenantId, created.id(), null).creditsBalance()
          );
          return new IdempotencyResponse(HttpStatus.OK.value(), toJson(response), null);
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(responseHeaders(httpRequest))
        .body(result.responseBodyJson());
  }

  @GetMapping("/agendas/{id}")
  public ResponseEntity<AgendaInfoResponse> getAgenda(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @PathVariable String id,
      HttpServletRequest request
  ) {
    Agenda agenda = schedulingService.getAgenda(tenantId, id);
    AgendaInfoResponse response = new AgendaInfoResponse(
        agenda.id(),
        agenda.name(),
        agenda.timezone(),
        agenda.workingHoursStart(),
        agenda.workingHoursEnd(),
        agenda.slotStepMin(),
        agenda.noShowFeeCents(),
        agenda.active(),
        agenda.createdAt(),
        agenda.updatedAt(),
        schedulingService.creditsStatus(tenantId, agenda.id(), null).creditsBalance()
    );
    return ResponseEntity.ok()
        .headers(responseHeaders(request))
        .body(response);
  }

  @PatchMapping("/agendas/{id}")
  public ResponseEntity<String> updateAgenda(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody AgendaUpdateRequest request,
      HttpServletRequest httpRequest
  ) {
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        request,
        transaction -> {
          Agenda updated = schedulingService.updateAgenda(tenantId, id, request, transaction);
          AgendaInfoResponse response = new AgendaInfoResponse(
              updated.id(),
              updated.name(),
              updated.timezone(),
              updated.workingHoursStart(),
              updated.workingHoursEnd(),
              updated.slotStepMin(),
              updated.noShowFeeCents(),
              updated.active(),
              updated.createdAt(),
              updated.updatedAt(),
              schedulingService.creditsStatus(tenantId, updated.id(), null).creditsBalance()
          );
          return new IdempotencyResponse(HttpStatus.OK.value(), toJson(response), null);
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(responseHeaders(httpRequest))
        .body(result.responseBodyJson());
  }
  @PostMapping("/servicos")
  public ResponseEntity<String> createService(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ServiceCreateRequest request,
      HttpServletRequest httpRequest
  ) {
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        request,
        transaction -> {
          ServiceDef created = schedulingService.createService(tenantId, request, transaction);
          ServiceResponse response = schedulingService.toResponse(created);
          return new IdempotencyResponse(HttpStatus.OK.value(), toJson(response), null);
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(responseHeaders(httpRequest))
        .body(result.responseBodyJson());
  }

  @GetMapping("/agenda")
  public ResponseEntity<AgendaResponse> agenda(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(defaultValue = "main") String agendaId,
      HttpServletRequest request
  ) {
    Instant fromInstant = Instant.parse(from);
    Instant toInstant = Instant.parse(to);
    AgendaResponse response = schedulingService.agenda(tenantId, agendaId, fromInstant, toInstant);
    return ResponseEntity.ok()
        .headers(responseHeaders(request))
        .body(response);
  }

  @GetMapping("/slots-disponiveis")
  public ResponseEntity<SlotsResponse> slotsDisponiveis(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestParam String serviceId,
      @RequestParam String date,
      @RequestParam(defaultValue = "main") String agendaId,
      @RequestParam(required = false) String zoneId,
      HttpServletRequest request
  ) {
    LocalDate parsedDate = LocalDate.parse(date);
    SlotsResponse response = schedulingService.availableSlots(tenantId, serviceId, parsedDate, agendaId, zoneId);
    return ResponseEntity.ok()
        .headers(responseHeaders(request))
        .body(response);
  }

  @PostMapping("/reservar")
  public ResponseEntity<String> reservar(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ReservationRequest request,
      HttpServletRequest httpRequest
  ) {
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        request,
        transaction -> {
          ReservationResponse response = schedulingService.reserve(tenantId, request, transaction);
          return new IdempotencyResponse(HttpStatus.OK.value(), toJson(response), null);
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(responseHeaders(httpRequest))
        .body(result.responseBodyJson());
  }

  @PostMapping("/confirmar")
  public ResponseEntity<String> confirmar(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ConfirmRequest request,
      HttpServletRequest httpRequest
  ) {
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        request,
        transaction -> {
          ConfirmResponse response = schedulingService.confirm(tenantId, request.appointmentId());
          return new IdempotencyResponse(HttpStatus.OK.value(), toJson(response), null);
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(responseHeaders(httpRequest))
        .body(result.responseBodyJson());
  }

  @PostMapping("/atendido")
  public ResponseEntity<String> attended(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody AttendedRequest request,
      HttpServletRequest httpRequest
  ) {
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        request,
        transaction -> {
          AttendedResponse response = schedulingService.attended(tenantId, request.appointmentId());
          return new IdempotencyResponse(HttpStatus.OK.value(), toJson(response), null);
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(responseHeaders(httpRequest))
        .body(result.responseBodyJson());
  }

  @PostMapping("/cancelar")
  public ResponseEntity<String> cancelar(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CancelRequest request,
      HttpServletRequest httpRequest
  ) {
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        request,
        transaction -> {
          CancelResponse response = schedulingService.cancel(tenantId, request.appointmentId());
          return new IdempotencyResponse(HttpStatus.OK.value(), toJson(response), null);
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(responseHeaders(httpRequest))
        .body(result.responseBodyJson());
  }

  @PostMapping("/notificar")
  public ResponseEntity<String> notificar(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody NotifyRequest request,
      HttpServletRequest httpRequest
  ) {
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        request,
        transaction -> {
          NotifyResponse response = schedulingService.notify(tenantId, request.appointmentId(), request.type(),
              traceId(httpRequest));
          return new IdempotencyResponse(HttpStatus.OK.value(), toJson(response), null);
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(responseHeaders(httpRequest))
        .body(result.responseBodyJson());
  }

  @PostMapping("/agendas/{id}/creditos/upgrade")
  public ResponseEntity<String> upgradeAgendaCredits(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody(required = false) AgendaCreditsUpgradeRequest request,
      HttpServletRequest httpRequest
  ) {
    AgendaCreditsUpgradeRequest payload = request == null ? new AgendaCreditsUpgradeRequest(null) : request;
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        payload,
        transaction -> {
          AgendaCreditsUpgradeResponse response = schedulingService.upgradeAgendaCredits(
              tenantId, id, payload, idempotencyKey);
          return new IdempotencyResponse(HttpStatus.OK.value(), toJson(response), null);
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(responseHeaders(httpRequest))
        .body(result.responseBodyJson());
  }

  @GetMapping("/agendas/{id}/creditos/status/{chargeId}")
  public ResponseEntity<AgendaCreditsStatusResponse> creditsStatus(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @PathVariable String id,
      @PathVariable String chargeId,
      HttpServletRequest request
  ) {
    AgendaCreditsStatusResponse response = schedulingService.creditsStatus(tenantId, id, chargeId);
    return ResponseEntity.ok()
        .headers(responseHeaders(request))
        .body(response);
  }

  @GetMapping("/multas")
  public ResponseEntity<List<FineResponse>> listFines(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestParam(required = false) String agendaId,
      @RequestParam(required = false) String status,
      HttpServletRequest request
  ) {
    FineStatus parsedStatus = status == null ? null : FineStatus.valueOf(status.toUpperCase());
    List<FineResponse> response = schedulingService.listFines(tenantId, agendaId, parsedStatus);
    return ResponseEntity.ok()
        .headers(responseHeaders(request))
        .body(response);
  }

  @PostMapping("/multas/{id}/waive")
  public ResponseEntity<String> waiveFine(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody(required = false) FineWaiveRequest request,
      HttpServletRequest httpRequest
  ) {
    FineWaiveRequest payload = request == null ? new FineWaiveRequest(null) : request;
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        payload,
        transaction -> {
          FineResponse response = schedulingService.waiveFine(tenantId, id);
          return new IdempotencyResponse(HttpStatus.OK.value(), toJson(response), null);
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(responseHeaders(httpRequest))
        .body(result.responseBodyJson());
  }

  private HttpHeaders responseHeaders(HttpServletRequest request) {
    HttpHeaders headers = new HttpHeaders();
    String requestId = requestId(request);
    if (StringUtils.hasText(requestId)) {
      headers.add("X-Request-Id", requestId);
    }
    return headers;
  }

  private String requestId(HttpServletRequest request) {
    String requestId = request.getHeader("X-Request-Id");
    if (StringUtils.hasText(requestId)) {
      return requestId;
    }
    String traceId = TraceIdUtils.resolveTraceId(request);
    if (StringUtils.hasText(traceId) && !"-".equals(traceId)) {
      return traceId;
    }
    return UUID.randomUUID().toString();
  }

  private String traceId(HttpServletRequest request) {
    return TraceIdUtils.resolveTraceId(request);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize response", e);
    }
  }
}
