package com.apipratudo.helpdesk.service;

import com.apipratudo.helpdesk.config.WhatsappProperties;
import com.apipratudo.helpdesk.dto.MessageCreateRequest;
import com.apipratudo.helpdesk.dto.TicketCreateRequest;
import com.apipratudo.helpdesk.model.MessageDirection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WhatsappWebhookService {

  private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookService.class);

  private final WhatsappProperties properties;
  private final HelpdeskService helpdeskService;
  private final ObjectMapper objectMapper;

  public WhatsappWebhookService(WhatsappProperties properties, HelpdeskService helpdeskService, ObjectMapper objectMapper) {
    this.properties = properties;
    this.helpdeskService = helpdeskService;
    this.objectMapper = objectMapper;
  }

  public boolean verifyToken(String token) {
    return StringUtils.hasText(properties.getVerifyToken()) && properties.getVerifyToken().equals(token);
  }

  public boolean validateSignature(String rawBody, String signatureHeader) {
    if (!StringUtils.hasText(signatureHeader) || !signatureHeader.startsWith("sha256=")) {
      return false;
    }
    String expected = signatureHeader.substring("sha256=".length());
    String secret = properties.getAppSecret();
    if (!StringUtils.hasText(secret)) {
      return false;
    }
    String computed = hmacSha256Hex(secret, rawBody);
    return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), computed.getBytes(StandardCharsets.UTF_8));
  }

  public void handleWebhook(String rawBody) {
    JsonNode payload = parse(rawBody);
    String phoneNumberId = findPhoneNumberId(payload);
    if (!StringUtils.hasText(phoneNumberId)) {
      log.warn("WhatsApp webhook missing phone_number_id");
      return;
    }
    String tenantId = helpdeskService.resolveTenantByPhoneNumberId(phoneNumberId);
    if (!StringUtils.hasText(tenantId)) {
      log.warn("WhatsApp binding not found phone_number_id={}", phoneNumberId);
      return;
    }
    String waId = findWaId(payload);
    String text = findText(payload);
    String providerMessageId = findMessageId(payload);
    if (!StringUtils.hasText(waId) || !StringUtils.hasText(text)) {
      log.warn("WhatsApp webhook missing wa_id/text for tenant={}", tenantId);
      return;
    }

    var ticket = helpdeskService.createTicket(tenantId, new TicketCreateRequest(waId));
    helpdeskService.addMessage(tenantId, ticket.id(), new MessageCreateRequest(
        MessageDirection.INBOUND.name(),
        text,
        providerMessageId
    ));
  }

  private JsonNode parse(String rawBody) {
    try {
      return objectMapper.readTree(rawBody);
    } catch (Exception ex) {
      throw new IllegalStateException("Invalid webhook payload", ex);
    }
  }

  private String findPhoneNumberId(JsonNode payload) {
    return Optional.ofNullable(payload.at("/entry/0/changes/0/value/metadata/phone_number_id"))
        .filter(JsonNode::isTextual)
        .map(JsonNode::asText)
        .orElse(null);
  }

  private String findWaId(JsonNode payload) {
    JsonNode contacts = payload.at("/entry/0/changes/0/value/contacts/0/wa_id");
    if (contacts.isTextual()) {
      return contacts.asText();
    }
    JsonNode from = payload.at("/entry/0/changes/0/value/messages/0/from");
    if (from.isTextual()) {
      return from.asText();
    }
    return null;
  }

  private String findText(JsonNode payload) {
    JsonNode text = payload.at("/entry/0/changes/0/value/messages/0/text/body");
    if (text.isTextual()) {
      return text.asText();
    }
    return null;
  }

  private String findMessageId(JsonNode payload) {
    JsonNode id = payload.at("/entry/0/changes/0/value/messages/0/id");
    if (id.isTextual()) {
      return id.asText();
    }
    return null;
  }

  private String hmacSha256Hex(String secret, String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to compute signature", ex);
    }
  }
}
