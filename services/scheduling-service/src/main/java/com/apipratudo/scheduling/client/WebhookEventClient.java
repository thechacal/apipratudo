package com.apipratudo.scheduling.client;

import com.apipratudo.scheduling.config.WebhookProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WebhookEventClient {

  private static final Logger log = LoggerFactory.getLogger(WebhookEventClient.class);

  private final WebClient webClient;
  private final Duration timeout;
  private final String serviceToken;

  public WebhookEventClient(WebClient.Builder builder, WebhookProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.serviceToken = properties.getServiceToken();
  }

  public void publishEvent(WebhookEventRequest request, String traceId) {
    if (!StringUtils.hasText(serviceToken) || !StringUtils.hasText(request.event())) {
      return;
    }

    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/events")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Service-Token", serviceToken);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    try {
      spec.bodyValue(request)
          .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty(""))
          .timeout(timeout)
          .block(timeout);
    } catch (Exception ex) {
      log.warn("Webhook event publish failed error={}", ex.getMessage());
    }
  }
}
