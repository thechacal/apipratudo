package com.apipratudo.billing.client;

import com.apipratudo.billing.config.QuotaClientProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private final ObjectMapper objectMapper;

  public QuotaClient(WebClient.Builder builder, QuotaClientProperties properties, ObjectMapper objectMapper) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.internalToken = properties.getInternalToken();
    this.objectMapper = objectMapper;
  }

  public AddCreditsResult addCredits(String apiKeyHash, long credits, String traceId) {
    if (!StringUtils.hasText(apiKeyHash)) {
      throw new IllegalArgumentException("Missing apiKeyHash for credits");
    }
    if (credits <= 0) {
      throw new IllegalArgumentException("Credits must be positive");
    }

    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/credits/add")
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
        "credits", credits
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
      throw new IllegalStateException("Quota service credits failed status=" + result.statusCode());
    }
    return new AddCreditsResult(parseCreditsRemaining(result.body()));
  }

  public record ClientResult(int statusCode, String body) {
  }

  public record AddCreditsResult(Long creditsRemaining) {
  }

  private Long parseCreditsRemaining(String body) {
    if (!StringUtils.hasText(body)) {
      return null;
    }
    try {
      JsonNode node = objectMapper.readTree(body);
      JsonNode value = node.get("creditsRemaining");
      if (value != null && value.isNumber()) {
        return value.longValue();
      }
    } catch (Exception ignored) {
      // best effort parsing
    }
    return null;
  }
}
