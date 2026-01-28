package com.apipratudo.scheduling.controller;

import com.apipratudo.scheduling.dto.AgendaResponse;
import com.apipratudo.scheduling.dto.CancelRequest;
import com.apipratudo.scheduling.dto.CancelResponse;
import com.apipratudo.scheduling.dto.ConfirmRequest;
import com.apipratudo.scheduling.dto.ConfirmResponse;
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
