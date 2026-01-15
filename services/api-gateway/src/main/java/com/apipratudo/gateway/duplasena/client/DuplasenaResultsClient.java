package com.apipratudo.gateway.duplasena.client;

import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class DuplasenaResultsClient {

  private final WebClient webClient;
  private final Duration timeout;

  public DuplasenaResultsClient(WebClient.Builder builder, DuplasenaResultsClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
  }

  public DuplasenaResultsClientResult getResultado(String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/v1/duplasena/resultado-oficial")
        .accept(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    DuplasenaResultsClientResult result = spec
        .exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new DuplasenaResultsClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Duplasena results service returned empty response");
    }

    return result;
  }

  public record DuplasenaResultsClientResult(int statusCode, String body) {
  }
}
