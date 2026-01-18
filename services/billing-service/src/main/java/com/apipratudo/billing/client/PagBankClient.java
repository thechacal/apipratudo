package com.apipratudo.billing.client;

import com.apipratudo.billing.config.PagBankProperties;
import com.apipratudo.billing.error.PagBankException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PagBankClient {

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final Duration timeout;
  private final String token;

  public PagBankClient(WebClient.Builder builder, PagBankProperties properties, ObjectMapper objectMapper) {
    this.webClient = builder.baseUrl(trimTrailingSlash(properties.getBaseUrl())).build();
    this.objectMapper = objectMapper;
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.token = properties.getToken();
  }

  public JsonNode createOrder(Map<String, Object> payload, String idempotencyKey) {
    return sendJson(HttpMethod.POST, "/orders", payload, idempotencyKey);
  }

  public JsonNode getOrder(String orderId) {
    return sendJson(HttpMethod.GET, "/orders/{id}", null, null, orderId);
  }

  public String fetchQrCodeBase64(String url) {
    if (!StringUtils.hasText(url)) {
      return "";
    }
    String auth = resolveToken();
    String body = webClient.get()
        .uri(url)
        .header("Authorization", auth)
        .header("Accept", "text/plain")
        .exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(data -> {
              if (response.statusCode().is2xxSuccessful()) {
                return data;
              }
              throw new PagBankException(response.statusCode().value(), data);
            }))
        .timeout(timeout)
        .block(timeout);
    return body == null ? "" : body.trim();
  }

  private JsonNode sendJson(HttpMethod method, String path, Object body, String idempotencyKey, Object... uriVars) {
    String auth = resolveToken();
    WebClient.RequestBodySpec spec = webClient.method(method)
        .uri(path, uriVars)
        .header("Authorization", auth)
        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
        .contentType(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(idempotencyKey)) {
      spec = spec.header("X-Idempotency-Key", idempotencyKey);
    }

    WebClient.RequestHeadersSpec<?> finalSpec = body == null ? spec : spec.bodyValue(body);

    String responseBody = finalSpec.exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(data -> {
              if (response.statusCode().is2xxSuccessful()) {
                return data;
              }
              throw new PagBankException(response.statusCode().value(), data);
            }))
        .timeout(timeout)
        .block(timeout);

    try {
      return objectMapper.readTree(responseBody == null ? "{}" : responseBody);
    } catch (Exception ex) {
      throw new PagBankException(502, "Invalid JSON from PagBank");
    }
  }

  private String resolveToken() {
    if (!StringUtils.hasText(token)) {
      throw new IllegalStateException("PAGBANK token not configured");
    }
    return "Bearer " + token.trim();
  }

  private String trimTrailingSlash(String baseUrl) {
    if (!StringUtils.hasText(baseUrl)) {
      return "";
    }
    String trimmed = baseUrl.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
