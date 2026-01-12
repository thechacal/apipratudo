package com.apipratudo.gateway.quota;

import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class QuotaClient {

  private final WebClient webClient;
  private final Duration timeout;

  public QuotaClient(WebClient.Builder builder, QuotaClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
  }

  public QuotaClientResult consume(String apiKey, String requestId, String route, int cost, String traceId) {
    QuotaConsumeRequest request = new QuotaConsumeRequest(apiKey, requestId, route, cost);

    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/quota/consume")
        .contentType(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    QuotaClientResult result = spec
        .bodyValue(request)
        .exchangeToMono(response -> response.bodyToMono(QuotaConsumeResponse.class)
            .defaultIfEmpty(new QuotaConsumeResponse(false, null, null, null, null))
            .map(body -> new QuotaClientResult(body.allowed(), body.reason(), response.statusCode().value())))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Quota service returned empty response");
    }

    return result;
  }
}
