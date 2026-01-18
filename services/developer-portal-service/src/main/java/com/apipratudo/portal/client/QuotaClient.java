package com.apipratudo.portal.client;

import com.apipratudo.portal.config.QuotaClientProperties;
import com.apipratudo.portal.dto.CreateFreeKeyRequest;
import com.apipratudo.portal.dto.CreateFreeKeyResponse;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class QuotaClient {

  private final WebClient webClient;
  private final Duration timeout;
  private final String portalToken;

  public QuotaClient(WebClient.Builder builder, QuotaClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.portalToken = properties.getPortalToken();
  }

  public CreateFreeKeyResponse createFreeKey(CreateFreeKeyRequest request, String traceId) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/internal/keys/create-free")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(portalToken)) {
      spec = spec.header("X-Portal-Token", portalToken);
    }
    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    CreateFreeKeyResponse response = spec.bodyValue(request)
        .retrieve()
        .bodyToMono(CreateFreeKeyResponse.class)
        .timeout(timeout)
        .block(timeout);

    if (response == null) {
      throw new IllegalStateException("Quota service returned empty response");
    }
    return response;
  }

  public ClientResult status(String apiKey, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/v1/quota/status")
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Api-Key", apiKey);

    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    ClientResult result = spec.exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new ClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Quota service returned empty response");
    }
    return result;
  }

  public record ClientResult(int statusCode, String body) {
  }
}
