package com.apipratudo.gateway.scheduling;

import com.apipratudo.gateway.scheduling.dto.CancelRequest;
import com.apipratudo.gateway.scheduling.dto.ConfirmRequest;
import com.apipratudo.gateway.scheduling.dto.NotifyRequest;
import com.apipratudo.gateway.scheduling.dto.ReservationRequest;
import com.apipratudo.gateway.scheduling.dto.ServiceCreateRequest;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SchedulingClient {

  private final WebClient webClient;
  private final Duration timeout;
  private final SchedulingClientProperties properties;

  public SchedulingClient(WebClient.Builder builder, SchedulingClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.properties = properties;
  }

  public SchedulingClientResult listServices(String tenantId, String requestId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/v1/servicos")
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, requestId, null));
  }

  public SchedulingClientResult createService(
      String tenantId,
      ServiceCreateRequest request,
      String idempotencyKey,
      String requestId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/servicos")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  public SchedulingClientResult agenda(
      String tenantId,
      String from,
      String to,
      String agendaId,
      String requestId
  ) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri(uriBuilder -> uriBuilder.path("/v1/agenda")
            .queryParam("from", from)
            .queryParam("to", to)
            .queryParam("agendaId", agendaId)
            .build())
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, requestId, null));
  }

  public SchedulingClientResult slotsDisponiveis(
      String tenantId,
      String serviceId,
      String date,
      String agendaId,
      String zoneId,
      String requestId
  ) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri(uriBuilder -> {
          uriBuilder.path("/v1/slots-disponiveis")
              .queryParam("serviceId", serviceId)
              .queryParam("date", date)
              .queryParam("agendaId", agendaId);
          if (StringUtils.hasText(zoneId)) {
            uriBuilder.queryParam("zoneId", zoneId);
          }
          return uriBuilder.build();
        })
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, requestId, null));
  }

  public SchedulingClientResult reservar(
      String tenantId,
      ReservationRequest request,
      String idempotencyKey,
      String requestId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/reservar")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  public SchedulingClientResult confirmar(
      String tenantId,
      ConfirmRequest request,
      String idempotencyKey,
      String requestId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/confirmar")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  public SchedulingClientResult cancelar(
      String tenantId,
      CancelRequest request,
      String idempotencyKey,
      String requestId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/cancelar")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  public SchedulingClientResult notificar(
      String tenantId,
      NotifyRequest request,
      String idempotencyKey,
      String requestId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/notificar")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  private WebClient.RequestHeadersSpec<?> applyHeaders(
      WebClient.RequestHeadersSpec<?> spec,
      String tenantId,
      String requestId,
      String idempotencyKey
  ) {
    spec = spec.header("X-Tenant-Id", tenantId);
    if (StringUtils.hasText(requestId)) {
      spec = spec.header("X-Request-Id", requestId);
    }
    if (StringUtils.hasText(idempotencyKey)) {
      spec = spec.header("Idempotency-Key", idempotencyKey);
    }
    if (StringUtils.hasText(properties.getServiceToken())) {
      spec = spec.header("X-Service-Token", properties.getServiceToken());
    }
    return spec;
  }

  private WebClient.RequestBodySpec applyBodyHeaders(
      WebClient.RequestBodySpec spec,
      String tenantId,
      String requestId,
      String idempotencyKey
  ) {
    spec = spec.header("X-Tenant-Id", tenantId);
    if (StringUtils.hasText(requestId)) {
      spec = spec.header("X-Request-Id", requestId);
    }
    if (StringUtils.hasText(idempotencyKey)) {
      spec = spec.header("Idempotency-Key", idempotencyKey);
    }
    if (StringUtils.hasText(properties.getServiceToken())) {
      spec = spec.header("X-Service-Token", properties.getServiceToken());
    }
    return spec;
  }

  private SchedulingClientResult exchange(WebClient.RequestHeadersSpec<?> spec) {
    SchedulingClientResult result = spec.exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new SchedulingClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Scheduling service returned empty response");
    }

    return result;
  }

  public record SchedulingClientResult(int statusCode, String body) {
  }
}
