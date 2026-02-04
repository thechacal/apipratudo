package com.apipratudo.gateway.identity;

import com.apipratudo.gateway.identity.dto.DocumentValidateRequest;
import com.apipratudo.gateway.identity.dto.VerificationRequest;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class IdentityVerifyClient {

  private final WebClient webClient;
  private final Duration timeout;
  private final IdentityVerifyClientProperties properties;

  public IdentityVerifyClient(WebClient.Builder builder, IdentityVerifyClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.properties = properties;
  }

  public IdentityVerifyClientResult validarDocumento(
      String tenantId,
      DocumentValidateRequest request,
      String requestId,
      String idempotencyKey
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/v1/documentos/validar")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  public IdentityVerifyClientResult cnpjStatus(
      String tenantId,
      String cnpj,
      String requestId
  ) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/internal/v1/cnpj/{cnpj}/status", cnpj)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, requestId, null));
  }

  public IdentityVerifyClientResult verificacoes(
      String tenantId,
      VerificationRequest request,
      String requestId,
      String idempotencyKey
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/v1/verificacoes")
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

  private IdentityVerifyClientResult exchange(WebClient.RequestHeadersSpec<?> spec) {
    IdentityVerifyClientResult result = spec.exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new IdentityVerifyClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Identity verify service returned empty response");
    }
    return result;
  }

  public record IdentityVerifyClientResult(int statusCode, String body) {
  }
}
