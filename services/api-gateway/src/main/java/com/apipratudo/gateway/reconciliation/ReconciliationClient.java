package com.apipratudo.gateway.reconciliation;

import com.apipratudo.gateway.reconciliation.dto.MatchRequest;
import com.apipratudo.gateway.reconciliation.dto.PaymentWebhookRequest;
import java.time.Duration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ReconciliationClient {

  private final WebClient webClient;
  private final Duration timeout;
  private final ReconciliationClientProperties properties;

  public ReconciliationClient(WebClient.Builder builder, ReconciliationClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.properties = properties;
  }

  public ReconciliationClientResult importarExtrato(
      String tenantId,
      byte[] fileBytes,
      String filename,
      String accountId,
      String requestId,
      String idempotencyKey
  ) {
    MultipartBodyBuilder mb = new MultipartBodyBuilder();
    mb.part("file", new ByteArrayResource(fileBytes) {
      @Override
      public String getFilename() {
        return filename;
      }
    }).contentType(MediaType.APPLICATION_OCTET_STREAM);
    if (StringUtils.hasText(accountId)) {
      mb.part("accountId", accountId);
    }

    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/importar-extrato")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .accept(MediaType.APPLICATION_JSON);

    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(mb.build()));
  }

  public ReconciliationClientResult match(String tenantId, MatchRequest request, String requestId, String idempotencyKey) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/match")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyBodyHeaders(spec, tenantId, requestId, idempotencyKey).bodyValue(request));
  }

  public ReconciliationClientResult conciliado(String tenantId, String importId, int page, int size, String requestId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri(uriBuilder -> uriBuilder.path("/v1/conciliado")
            .queryParam("importId", importId)
            .queryParam("page", page)
            .queryParam("size", size)
            .build())
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, requestId, null));
  }

  public ReconciliationClientResult pendencias(
      String tenantId,
      String importId,
      String tipo,
      int page,
      int size,
      String requestId
  ) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri(uriBuilder -> {
          var b = uriBuilder.path("/v1/pendencias")
              .queryParam("importId", importId)
              .queryParam("page", page)
              .queryParam("size", size);
          if (StringUtils.hasText(tipo)) {
            b.queryParam("tipo", tipo);
          }
          return b.build();
        })
        .accept(MediaType.APPLICATION_JSON);
    return exchange(applyHeaders(spec, tenantId, requestId, null));
  }

  public ReconciliationClientResult webhookPagamento(
      String tenantId,
      PaymentWebhookRequest request,
      String requestId,
      String idempotencyKey
  ) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/webhook/pagamento")
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

  private ReconciliationClientResult exchange(WebClient.RequestHeadersSpec<?> spec) {
    ReconciliationClientResult result = spec.exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new ReconciliationClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Reconciliation service returned empty response");
    }

    return result;
  }

  public record ReconciliationClientResult(int statusCode, String body) {
  }
}
