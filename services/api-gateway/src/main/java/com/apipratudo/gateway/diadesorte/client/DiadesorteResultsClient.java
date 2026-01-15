package com.apipratudo.gateway.diadesorte.client;

import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class DiadesorteResultsClient {

  private final WebClient webClient;
  private final Duration timeout;

  public DiadesorteResultsClient(WebClient.Builder builder, DiadesorteResultsClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
  }

  public DiadesorteResultsClientResult getResultado(String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/v1/diadesorte/resultado-oficial")
        .accept(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    DiadesorteResultsClientResult result = spec
        .exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new DiadesorteResultsClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Diadesorte results service returned empty response");
    }

    return result;
  }

  public record DiadesorteResultsClientResult(int statusCode, String body) {
  }
}
