package com.apipratudo.portal.client;

import com.apipratudo.portal.config.QuotaClientProperties;
import com.apipratudo.portal.dto.CreateFreeKeyRequest;
import com.apipratudo.portal.dto.CreateFreeKeyResponse;
import com.apipratudo.portal.error.QuotaServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private final ObjectMapper objectMapper;

  public QuotaClient(WebClient.Builder builder, QuotaClientProperties properties, ObjectMapper objectMapper) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.portalToken = properties.getPortalToken();
    this.objectMapper = objectMapper;
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

    ClientResult result = exchange(spec.bodyValue(request));
    if (result.statusCode() >= 200 && result.statusCode() < 300) {
      try {
        return objectMapper.readValue(result.body(), CreateFreeKeyResponse.class);
      } catch (Exception ex) {
        throw new IllegalStateException("Quota service returned invalid response");
      }
    }
    throw toException(result);
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

  private ClientResult exchange(WebClient.RequestHeadersSpec<?> spec) {
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

  private QuotaServiceException toException(ClientResult result) {
    int status = result.statusCode();
    String error = null;
    String message = null;
    String body = result.body();

    if (StringUtils.hasText(body)) {
      try {
        JsonNode node = objectMapper.readTree(body);
        error = text(node, "error");
        message = text(node, "message");
      } catch (Exception ignored) {
        // fall back to generic mapping
      }
    }

    if (!StringUtils.hasText(error)) {
      if (status == 401) {
        error = "UNAUTHORIZED";
      } else if (status == 403) {
        error = "FORBIDDEN";
      } else {
        error = "QUOTA_SERVICE_ERROR";
      }
    }

    if (!StringUtils.hasText(message)) {
      if (status == 401 || status == 403) {
        message = "Quota service authentication failed";
      } else if (status >= 500) {
        message = "Quota service unavailable";
      } else {
        message = "Quota service error";
      }
    }

    return new QuotaServiceException(status, error, message);
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    String text = value.asText();
    return text == null ? null : text.trim();
  }

  public record ClientResult(int statusCode, String body) {
  }
}
