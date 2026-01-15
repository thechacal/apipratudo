package com.apipratudo.gateway.loteca.client;

import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class LotecaResultsClient {

  private final WebClient webClient;
  private final Duration timeout;

  public LotecaResultsClient(WebClient.Builder builder, LotecaResultsClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
  }

  public LotecaResultsClientResult getResultado(String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/v1/loteca/resultado-oficial")
        .accept(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    LotecaResultsClientResult result = spec
        .exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new LotecaResultsClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Loteca results service returned empty response");
    }

    return result;
  }

  public record LotecaResultsClientResult(int statusCode, String body) {
  }
}
