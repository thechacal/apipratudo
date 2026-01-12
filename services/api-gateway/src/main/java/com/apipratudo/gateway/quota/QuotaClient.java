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
  private final String internalToken;

  public QuotaClient(WebClient.Builder builder, QuotaClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.internalToken = properties.getInternalToken();
  }

  public QuotaClientResult consume(String apiKey, String requestId, String route, int cost, String traceId) {
    QuotaConsumeRequest request = new QuotaConsumeRequest(apiKey, requestId, route, cost);

    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/quota/consume")
        .contentType(MediaType.APPLICATION_JSON);

    String tokenValue = internalToken == null ? "" : internalToken;
    spec = spec.header("X-Internal-Token", tokenValue);
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

  public void refund(String apiKey, String requestId, String traceId) {
    QuotaRefundRequest request = new QuotaRefundRequest(apiKey, requestId);

    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/quota/refund")
        .contentType(MediaType.APPLICATION_JSON);

    String tokenValue = internalToken == null ? "" : internalToken;
    spec = spec.header("X-Internal-Token", tokenValue);
    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    spec.bodyValue(request)
        .exchangeToMono(response -> response.bodyToMono(Void.class))
        .timeout(timeout)
        .block(timeout);
  }
}
