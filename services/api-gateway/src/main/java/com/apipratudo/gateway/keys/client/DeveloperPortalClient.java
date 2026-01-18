package com.apipratudo.gateway.keys.client;

import com.apipratudo.gateway.keys.dto.KeyRequest;
import com.apipratudo.gateway.keys.dto.KeyUpgradeRequest;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class DeveloperPortalClient {

  private final WebClient webClient;
  private final Duration timeout;

  public DeveloperPortalClient(WebClient.Builder builder, DeveloperPortalClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
  }

  public PortalClientResult requestKey(KeyRequest request, String traceId) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/keys/request")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    return exchange(spec.bodyValue(request));
  }

  public PortalClientResult status(String apiKey, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/v1/keys/status")
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Api-Key", apiKey);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    return exchange(spec);
  }

  public PortalClientResult upgrade(String apiKey, KeyUpgradeRequest request, String traceId) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/keys/upgrade")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Api-Key", apiKey);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    return exchange(spec.bodyValue(request));
  }

  public PortalClientResult upgradeStatus(String apiKey, String chargeId, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/v1/keys/upgrade/{chargeId}", chargeId)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Api-Key", apiKey);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    return exchange(spec);
  }

  private PortalClientResult exchange(WebClient.RequestHeadersSpec<?> spec) {
    PortalClientResult result = spec.exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new PortalClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Developer portal service returned empty response");
    }

    return result;
  }

  public record PortalClientResult(int statusCode, String body) {
  }
}
