package com.apipratudo.billing.client;

import com.apipratudo.billing.config.QuotaClientProperties;
import java.time.Duration;
import java.util.Map;
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

  public void activatePremium(String apiKeyHash, String plan, String traceId) {
    if (!StringUtils.hasText(apiKeyHash)) {
      throw new IllegalArgumentException("Missing apiKeyHash for activation");
    }

    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/subscriptions/activate-premium")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(internalToken)) {
      spec = spec.header("X-Internal-Token", internalToken);
    }
    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    Map<String, Object> body = Map.of(
        "apiKeyHash", apiKeyHash,
        "plan", plan
    );

    ClientResult result = spec.bodyValue(body)
        .exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(data -> new ClientResult(response.statusCode().value(), data)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Quota service returned empty response");
    }
    if (result.statusCode() < 200 || result.statusCode() >= 300) {
      throw new IllegalStateException("Quota service activation failed status=" + result.statusCode());
    }
  }

  public record ClientResult(int statusCode, String body) {
  }
}
