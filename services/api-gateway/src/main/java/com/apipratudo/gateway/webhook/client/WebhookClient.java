package com.apipratudo.gateway.webhook.client;

import com.apipratudo.gateway.webhook.dto.WebhookCreateResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WebhookClient {

  private final WebClient webClient;
  private final Duration timeout;
  private final String serviceToken;

  public WebhookClient(WebClient.Builder builder, WebhookClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.serviceToken = properties.getServiceToken();
  }

  public WebhookClientResult create(
      String apiKey,
      String idempotencyKey,
      String targetUrl,
      List<String> events,
      String secret,
      String traceId
  ) {
    WebhookServiceCreateRequest payload = new WebhookServiceCreateRequest(
        targetUrl,
        events,
        secret
    );

    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Api-Key", apiKey);

    if (StringUtils.hasText(idempotencyKey)) {
      spec = spec.header("Idempotency-Key", idempotencyKey);
    }
    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    WebhookClientResult result = spec
        .bodyValue(payload)
        .exchangeToMono(response -> response.bodyToMono(WebhookCreateResponse.class)
            .map(body -> new WebhookClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null || result.response() == null) {
      throw new IllegalStateException("Webhook service returned empty response");
    }

    return result;
  }

  public WebhookClientRawResult list(String apiKey, String limit, String cursor, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri(uriBuilder -> {
          uriBuilder.path("/v1/webhooks");
          if (StringUtils.hasText(limit)) {
            uriBuilder.queryParam("limit", limit);
          }
          if (StringUtils.hasText(cursor)) {
            uriBuilder.queryParam("cursor", cursor);
          }
          return uriBuilder.build();
        })
        .header("X-Api-Key", apiKey);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    WebhookClientRawResult result = spec
        .exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new WebhookClientRawResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Webhook service returned empty response");
    }

    return result;
  }

  public WebhookClientRawResult get(String apiKey, String id, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/v1/webhooks/{id}", id)
        .header("X-Api-Key", apiKey);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    WebhookClientRawResult result = spec
        .exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new WebhookClientRawResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Webhook service returned empty response");
    }

    return result;
  }

  public void publishEvent(WebhookEventRequest request, String traceId) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/events")
        .contentType(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(serviceToken)) {
      spec = spec.header("X-Service-Token", serviceToken);
    }
    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    WebhookClientRawResult result = spec
        .bodyValue(request)
        .exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new WebhookClientRawResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Webhook service returned empty response");
    }
    if (result.statusCode() >= 300) {
      throw new IllegalStateException("Webhook event publish failed status=" + result.statusCode());
    }
  }
}
