package com.apipratudo.scheduling.client;

import com.apipratudo.scheduling.config.BillingSaasProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BillingSaasClient {

  private final WebClient webClient;
  private final Duration timeout;
  private final String serviceToken;
  private final ObjectMapper objectMapper;

  public BillingSaasClient(
      WebClient.Builder builder,
      BillingSaasProperties properties,
      ObjectMapper objectMapper
  ) {
    this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.timeout = Duration.ofMillis(properties.getTimeoutMs());
    this.serviceToken = properties.getServiceToken();
    this.objectMapper = objectMapper;
  }

  public BillingCustomerResponse createCustomer(String tenantId, BillingCustomerCreateRequest request) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/clientes")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Tenant-Id", tenantId);
    if (StringUtils.hasText(serviceToken)) {
      spec = spec.header("X-Service-Token", serviceToken);
    }
    String body = spec.bodyValue(request)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(timeout)
        .block(timeout);
    return parseCustomer(body);
  }

  public BillingChargeResponse createCharge(String tenantId, BillingChargeCreateRequest request, String idempotencyKey) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/cobrancas")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Tenant-Id", tenantId);
    if (StringUtils.hasText(serviceToken)) {
      spec = spec.header("X-Service-Token", serviceToken);
    }
    if (StringUtils.hasText(idempotencyKey)) {
      spec = spec.header("Idempotency-Key", idempotencyKey);
    }
    String body = spec.bodyValue(request)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(timeout)
        .block(timeout);
    return parseCharge(body);
  }

  public BillingPixGenerateResponse generatePix(String tenantId, BillingPixGenerateRequest request, String idempotencyKey) {
    WebClient.RequestBodySpec spec = webClient.post()
        .uri("/internal/pix/gerar")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Tenant-Id", tenantId);
    if (StringUtils.hasText(serviceToken)) {
      spec = spec.header("X-Service-Token", serviceToken);
    }
    if (StringUtils.hasText(idempotencyKey)) {
      spec = spec.header("Idempotency-Key", idempotencyKey);
    }
    String body = spec.bodyValue(request)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(timeout)
        .block(timeout);
    return parsePix(body);
  }

  public BillingChargeStatusResponse getChargeStatus(String tenantId, String chargeId) {
    WebClient.RequestHeadersSpec<?> spec = webClient.get()
        .uri("/internal/cobrancas/{id}/status", chargeId)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Tenant-Id", tenantId);
    if (StringUtils.hasText(serviceToken)) {
      spec = spec.header("X-Service-Token", serviceToken);
    }
    String body = spec.retrieve()
        .bodyToMono(String.class)
        .timeout(timeout)
        .block(timeout);
    return parseChargeStatus(body);
  }

  private BillingCustomerResponse parseCustomer(String body) {
    try {
      JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
      return new BillingCustomerResponse(root.path("id").asText(null));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to parse billing customer", ex);
    }
  }

  private BillingChargeResponse parseCharge(String body) {
    try {
      JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
      return new BillingChargeResponse(root.path("id").asText(null), root.path("status").asText(null));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to parse billing charge", ex);
    }
  }

  private BillingChargeStatusResponse parseChargeStatus(String body) {
    try {
      JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
      return new BillingChargeStatusResponse(root.path("id").asText(null), root.path("status").asText(null));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to parse billing status", ex);
    }
  }

  private BillingPixGenerateResponse parsePix(String body) {
    try {
      JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
      JsonNode pix = root.path("pix");
      String providerChargeId = pix.path("providerChargeId").asText(null);
      String pixCopyPaste = pix.path("pixCopyPaste").asText(null);
      Instant expiresAt = null;
      if (!pix.path("expiresAt").isMissingNode() && !pix.path("expiresAt").isNull()) {
        expiresAt = Instant.parse(pix.path("expiresAt").asText());
      }
      return new BillingPixGenerateResponse(
          root.path("chargeId").asText(null),
          root.path("status").asText(null),
          providerChargeId,
          pixCopyPaste,
          expiresAt
      );
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to parse billing pix", ex);
    }
  }
}
