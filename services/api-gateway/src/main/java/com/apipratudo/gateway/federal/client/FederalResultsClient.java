package com.apipratudo.gateway.federal.client;

import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class FederalResultsClient {

  private final WebClient webClient;
  private final Duration timeout;

  public FederalResultsClient(WebClient.Builder builder, FederalResultsClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
  }

  public FederalResultsClientResult getResultado(String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/v1/federal/resultado-oficial")
        .accept(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    FederalResultsClientResult result = spec
        .exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new FederalResultsClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Federal results service returned empty response");
    }

    return result;
  }

  public record FederalResultsClientResult(int statusCode, String body) {
  }
}
