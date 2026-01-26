package com.apipratudo.gateway.billingsaas;

import com.apipratudo.gateway.billingsaas.dto.ChargeCreateRequest;
import com.apipratudo.gateway.billingsaas.dto.CustomerCreateRequest;
import com.apipratudo.gateway.billingsaas.dto.PagbankConnectRequest;
import com.apipratudo.gateway.billingsaas.dto.PixGenerateRequest;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BillingSaasClient {

  private final WebClient webClient;
  private final Duration timeout;
  private final BillingSaasClientProperties properties;

  public BillingSaasClient(WebClient.Builder builder, BillingSaasClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.properties = properties;
  }

  public BillingSaasClientResult createCustomer(
      String tenantId,
      CustomerCreateRequest request,
      String idempotencyKey,
      String traceId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/clientes")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, idempotencyKey, traceId).bodyValue(request));
  }

  public BillingSaasClientResult getCustomer(String tenantId, String id, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/internal/clientes/{id}", id)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, null, traceId));
  }

  public BillingSaasClientResult createCharge(
      String tenantId,
      ChargeCreateRequest request,
      String idempotencyKey,
      String traceId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/cobrancas")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, idempotencyKey, traceId).bodyValue(request));
  }

  public BillingSaasClientResult getCharge(String tenantId, String id, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/internal/cobrancas/{id}", id)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, null, traceId));
  }

  public BillingSaasClientResult getChargeStatus(String tenantId, String id, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/internal/cobrancas/{id}/status", id)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, null, traceId));
  }

  public BillingSaasClientResult generatePix(
      String tenantId,
      PixGenerateRequest request,
      String idempotencyKey,
      String traceId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/pix/gerar")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, idempotencyKey, traceId).bodyValue(request));
  }

  public BillingSaasClientResult webhook(String body, String webhookSecret, String traceId, String contentType) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/pix/webhook")
        .contentType(resolveContentType(contentType))
        .accept(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(properties.getWebhookSecret())) {
      spec = spec.header("X-Webhook-Secret", properties.getWebhookSecret());
    } else if (StringUtils.hasText(webhookSecret)) {
      spec = spec.header("X-Webhook-Secret", webhookSecret);
    }
    spec = applyServiceToken(spec);
    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    return exchange(spec.bodyValue(body));
  }

  public BillingSaasClientResult report(String tenantId, String from, String to, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri(uriBuilder -> {
          uriBuilder.path("/internal/relatorios");
          if (StringUtils.hasText(from)) {
            uriBuilder.queryParam("from", from);
          }
          if (StringUtils.hasText(to)) {
            uriBuilder.queryParam("to", to);
          }
          return uriBuilder.build();
        })
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, null, traceId));
  }

  public BillingSaasClientResult connectPagbank(
      String tenantId,
      PagbankConnectRequest request,
      String traceId
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/providers/pagbank/connect")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, null, traceId).bodyValue(request));
  }

  public BillingSaasClientResult pagbankStatus(String tenantId, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/internal/providers/pagbank/status")
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, null, traceId));
  }

  public BillingSaasClientResult disconnectPagbank(String tenantId, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.delete()
        .uri("/internal/providers/pagbank/disconnect")
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, null, traceId));
  }

  private WebClient.RequestHeadersSpec<?> applyHeaders(
      WebClient.RequestHeadersSpec<?> spec,
      String tenantId,
      String idempotencyKey,
      String traceId
  ) {
    spec = spec.header("X-Tenant-Id", tenantId);
    if (StringUtils.hasText(idempotencyKey)) {
      spec = spec.header("Idempotency-Key", idempotencyKey);
    }
    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }
    return applyServiceToken(spec);
  }

  private WebClient.RequestBodySpec applyBodyHeaders(
      WebClient.RequestBodySpec spec,
      String tenantId,
      String idempotencyKey,
      String traceId
  ) {
    spec = spec.header("X-Tenant-Id", tenantId);
    if (StringUtils.hasText(idempotencyKey)) {
      spec = spec.header("Idempotency-Key", idempotencyKey);
    }
    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }
    return applyServiceToken(spec);
  }

  private <T extends WebClient.RequestHeadersSpec<?>> T applyServiceToken(T spec) {
    if (StringUtils.hasText(properties.getServiceToken())) {
      spec = (T) spec.header("X-Service-Token", properties.getServiceToken());
    }
    return spec;
  }

  private BillingSaasClientResult exchange(WebClient.RequestHeadersSpec<?> spec) {
    BillingSaasClientResult result = spec.exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new BillingSaasClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Billing SaaS service returned empty response");
    }

    return result;
  }

  private MediaType resolveContentType(String contentType) {
    if (!StringUtils.hasText(contentType)) {
      return MediaType.APPLICATION_JSON;
    }
    try {
      return MediaType.valueOf(contentType);
    } catch (IllegalArgumentException ex) {
      return MediaType.APPLICATION_JSON;
    }
  }

  public record BillingSaasClientResult(int statusCode, String body) {
  }
}
