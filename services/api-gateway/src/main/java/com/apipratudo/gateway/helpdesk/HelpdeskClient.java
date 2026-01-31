package com.apipratudo.gateway.helpdesk;

import com.apipratudo.gateway.helpdesk.dto.AssignRequest;
import com.apipratudo.gateway.helpdesk.dto.MessageCreateRequest;
import com.apipratudo.gateway.helpdesk.dto.StatusUpdateRequest;
import com.apipratudo.gateway.helpdesk.dto.TemplateCreateRequest;
import com.apipratudo.gateway.helpdesk.dto.TicketCreateRequest;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class HelpdeskClient {

  private final WebClient webClient;
  private final Duration timeout;
  private final HelpdeskClientProperties properties;

  public HelpdeskClient(WebClient.Builder builder, HelpdeskClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.properties = properties;
  }

  public HelpdeskClientResult listTickets(String tenantId, String requestId, String status) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri(uriBuilder -> {
          var builderRef = uriBuilder.path("/internal/helpdesk/tickets");
          if (StringUtils.hasText(status)) {
            builderRef.queryParam("status", status);
          }
          return builderRef.build();
        })
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, requestId, null));
  }

  public HelpdeskClientResult createTicket(
      String tenantId,
      TicketCreateRequest request,
      String idempotencyKey,
      String requestId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/helpdesk/tickets")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  public HelpdeskClientResult getTicket(String tenantId, String ticketId, String requestId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/internal/helpdesk/tickets/{ticketId}", ticketId)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, requestId, null));
  }

  public HelpdeskClientResult listMessages(String tenantId, String ticketId, String requestId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/internal/helpdesk/tickets/{ticketId}/mensagens", ticketId)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, requestId, null));
  }

  public HelpdeskClientResult createMessage(
      String tenantId,
      String ticketId,
      MessageCreateRequest request,
      String idempotencyKey,
      String requestId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/helpdesk/tickets/{ticketId}/mensagens", ticketId)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  public HelpdeskClientResult assignTicket(
      String tenantId,
      String ticketId,
      AssignRequest request,
      String idempotencyKey,
      String requestId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/helpdesk/tickets/{ticketId}/atribuir", ticketId)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  public HelpdeskClientResult updateStatus(
      String tenantId,
      String ticketId,
      StatusUpdateRequest request,
      String idempotencyKey,
      String requestId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/helpdesk/tickets/{ticketId}/status", ticketId)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  public HelpdeskClientResult listTemplates(String tenantId, String requestId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/internal/helpdesk/templates")
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, requestId, null));
  }

  public HelpdeskClientResult createTemplate(
      String tenantId,
      TemplateCreateRequest request,
      String idempotencyKey,
      String requestId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/helpdesk/templates")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  public HelpdeskClientResult webhookVerify(String mode, String verifyToken, String challenge) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri(uriBuilder -> {
          var builderRef = uriBuilder.path("/internal/helpdesk/webhook/whatsapp");
          if (StringUtils.hasText(mode)) {
            builderRef.queryParam("hub.mode", mode);
          }
          if (StringUtils.hasText(verifyToken)) {
            builderRef.queryParam("hub.verify_token", verifyToken);
          }
          if (StringUtils.hasText(challenge)) {
            builderRef.queryParam("hub.challenge", challenge);
          }
          return builderRef.build();
        });
    return exchange(applyHeaders(spec, null, null, null));
  }

  public HelpdeskClientResult webhookReceive(byte[] rawBodyBytes, String contentType, String signature) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/helpdesk/webhook/whatsapp");
    if (StringUtils.hasText(contentType)) {
      spec = spec.contentType(MediaType.parseMediaType(contentType));
    } else {
      spec = spec.contentType(MediaType.APPLICATION_JSON);
    }
    spec = spec
        .accept(MediaType.APPLICATION_JSON);
    if (StringUtils.hasText(signature)) {
      spec = spec.header("X-Hub-Signature-256", signature);
    }
    return exchange(applyBodyHeaders(spec, null, null, null).bodyValue(rawBodyBytes));
  }

  private WebClient.RequestHeadersSpec<?> applyHeaders(
      WebClient.RequestHeadersSpec<?> spec,
      String tenantId,
      String requestId,
      String idempotencyKey
  ) {
    if (StringUtils.hasText(tenantId)) {
      spec = spec.header("X-Tenant-Id", tenantId);
    }
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
    if (StringUtils.hasText(tenantId)) {
      spec = spec.header("X-Tenant-Id", tenantId);
    }
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

  private HelpdeskClientResult exchange(WebClient.RequestHeadersSpec<?> spec) {
    HelpdeskClientResult result = spec.exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new HelpdeskClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Helpdesk service returned empty response");
    }

    return result;
  }

  public record HelpdeskClientResult(int statusCode, String body) {
  }
}
