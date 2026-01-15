package com.apipratudo.gateway.lotomania.client;

import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class LotomaniaResultsClient {

  private final WebClient webClient;
  private final Duration timeout;

  public LotomaniaResultsClient(WebClient.Builder builder, LotomaniaResultsClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
  }

  public LotomaniaResultsClientResult getResultado(String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/v1/lotomania/resultado-oficial")
        .accept(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    LotomaniaResultsClientResult result = spec
        .exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new LotomaniaResultsClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Lotomania results service returned empty response");
    }

    return result;
  }

  public record LotomaniaResultsClientResult(int statusCode, String body) {
  }
}
