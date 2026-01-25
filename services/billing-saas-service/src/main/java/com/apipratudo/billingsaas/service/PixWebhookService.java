package com.apipratudo.billingsaas.service;

import com.apipratudo.billingsaas.model.PagbankProviderConfig;
import com.apipratudo.billingsaas.model.PixProviderIndex;
import com.apipratudo.billingsaas.repository.PixProviderIndexStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PixWebhookService {

  private final ChargeService chargeService;
  private final PixProviderIndexStore pixProviderIndexStore;
  private final PagbankProviderService pagbankProviderService;
  private final ObjectMapper objectMapper;

  public PixWebhookService(
      ChargeService chargeService,
      PixProviderIndexStore pixProviderIndexStore,
      PagbankProviderService pagbankProviderService,
      ObjectMapper objectMapper
  ) {
    this.chargeService = chargeService;
    this.pixProviderIndexStore = pixProviderIndexStore;
    this.pagbankProviderService = pagbankProviderService;
    this.objectMapper = objectMapper;
  }

  public WebhookResult handle(byte[] rawBody, String contentType, String signature) {
    if (isFormWebhook(contentType)) {
      return WebhookResult.ok("form_ack", null);
    }

    JsonNode payload = parseJsonSafely(rawBody);
    if (payload == null) {
      return WebhookResult.ok("non_json", null);
    }

    String simplifiedProvider = text(payload, "provider");
    if (StringUtils.hasText(simplifiedProvider)) {
      if (!"FAKE".equalsIgnoreCase(simplifiedProvider)) {
        return WebhookResult.unauthorized("Unsupported provider payload");
      }
      String providerChargeId = text(payload, "providerChargeId");
      if (!StringUtils.hasText(providerChargeId)) {
        return WebhookResult.ok("ignored", "missing_provider_charge_id");
      }
      String event = text(payload, "event");
      if (!isPaidEvent(event)) {
        return WebhookResult.ok("ignored", "event_not_paid");
      }
      chargeService.handleWebhookPaid("FAKE", providerChargeId, parseInstant(payload, "paidAt"));
      return WebhookResult.ok("fake_processed", null);
    }

    String providerChargeId = text(payload, "id");
    String statusTop = normalizeStatus(text(payload, "status"));
    String statusCharge = extractChargeStatus(payload);
    if (!isPaidStatus(statusTop, statusCharge)) {
      return WebhookResult.ok("ignored", "status_not_paid");
    }
    if (!StringUtils.hasText(providerChargeId)) {
      return WebhookResult.ok("ignored", "missing_provider_charge_id");
    }

    PixProviderIndex index = pixProviderIndexStore.findByProviderChargeId("PAGBANK", providerChargeId)
        .orElse(null);
    if (index == null) {
      return WebhookResult.ok("ignored", "provider_charge_not_found");
    }

    PagbankProviderConfig config = pagbankProviderService.requireConfig(index.tenantId());
    String token = pagbankProviderService.resolveWebhookToken(config);
    if (!verifySignature(rawBody, signature, token)) {
      return WebhookResult.unauthorized("Invalid x-authenticity-token signature");
    }

    chargeService.handleWebhookPaid("PAGBANK", providerChargeId, Instant.now());
    return WebhookResult.ok("pagbank_processed", null);
  }

  private boolean isFormWebhook(String contentType) {
    String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    return normalized.contains("application/x-www-form-urlencoded");
  }

  private boolean isPaidEvent(String event) {
    if (!StringUtils.hasText(event)) {
      return false;
    }
    String normalized = event.trim().toUpperCase(Locale.ROOT);
    return "PAID".equals(normalized) || "CONFIRMED".equals(normalized);
  }

  private boolean isPaidStatus(String statusTop, String statusCharge) {
    return isPaidEvent(statusTop) || isPaidEvent(statusCharge);
  }

  private String normalizeStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return null;
    }
    return status.trim().toUpperCase(Locale.ROOT);
  }

  private String extractChargeStatus(JsonNode response) {
    JsonNode charges = response.path("charges");
    if (!charges.isArray() || charges.isEmpty()) {
      return null;
    }
    JsonNode charge = charges.get(0);
    return normalizeStatus(text(charge, "status"));
  }

  private Instant parseInstant(JsonNode payload, String field) {
    String value = text(payload, field);
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (Exception ex) {
      return null;
    }
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

  private boolean verifySignature(byte[] rawBody, String signature, String token) {
    if (!StringUtils.hasText(token) || !StringUtils.hasText(signature)) {
      return false;
    }
    byte[] prefix = (token + "-").getBytes(StandardCharsets.UTF_8);
    byte[] combined = new byte[prefix.length + rawBody.length];
    System.arraycopy(prefix, 0, combined, 0, prefix.length);
    System.arraycopy(rawBody, 0, combined, prefix.length, rawBody.length);
    String expected = sha256Hex(combined);
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        signature.trim().getBytes(StandardCharsets.UTF_8)
    );
  }

  private String sha256Hex(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(data);
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }

  private JsonNode parseJsonSafely(byte[] rawBody) {
    if (rawBody == null || rawBody.length == 0) {
      return null;
    }
    try {
      return objectMapper.readTree(rawBody);
    } catch (Exception ex) {
      return null;
    }
  }

  public record WebhookResult(boolean ok, int status, String mode, String warning, String message) {
    static WebhookResult ok(String mode, String warning) {
      return new WebhookResult(true, HttpStatus.OK.value(), mode, warning, null);
    }

    static WebhookResult unauthorized(String message) {
      return new WebhookResult(false, HttpStatus.UNAUTHORIZED.value(), null, null, message);
    }
  }
}
