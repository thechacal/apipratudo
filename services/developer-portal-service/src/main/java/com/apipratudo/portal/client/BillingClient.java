package com.apipratudo.portal.client;

import com.apipratudo.portal.config.BillingClientProperties;
import com.apipratudo.portal.dto.BillingChargeRequest;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BillingClient {

  private final WebClient webClient;
  private final Duration timeout;
  private final String serviceToken;

  public BillingClient(WebClient.Builder builder, BillingClientProperties properties) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.serviceToken = properties.getServiceToken();
  }

  public ClientResult createCharge(BillingChargeRequest request, String traceId) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/v1/billing/pix/charges")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(serviceToken)) {
      spec = spec.header("X-Service-Token", serviceToken);
    }
    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    ClientResult result = spec.bodyValue(request)
        .exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new ClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Billing service returned empty response");
    }
    return result;
  }

  public ClientResult chargeStatus(String chargeId, String traceId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/v1/billing/pix/charges/{chargeId}", chargeId)
        .accept(MediaType.APPLICATION_JSON);

    if (StringUtils.hasText(serviceToken)) {
      spec = spec.header("X-Service-Token", serviceToken);
    }
    if (StringUtils.hasText(traceId)) {
      spec = spec.header("X-Trace-Id", traceId);
    }

    ClientResult result = spec.exchangeToMono(response -> response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new ClientResult(response.statusCode().value(), body)))
        .timeout(timeout)
        .block(timeout);

    if (result == null) {
      throw new IllegalStateException("Billing service returned empty response");
    }
    return result;
  }

  public record ClientResult(int statusCode, String body) {
  }
}
