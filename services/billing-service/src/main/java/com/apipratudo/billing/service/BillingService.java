package com.apipratudo.billing.service;

import com.apipratudo.billing.client.PagBankClient;
import com.apipratudo.billing.client.QuotaClient;
import com.apipratudo.billing.config.PagBankProperties;
import com.apipratudo.billing.config.WebhookProperties;
import com.apipratudo.billing.dto.BillingChargeRequest;
import com.apipratudo.billing.dto.BillingChargeResponse;
import com.apipratudo.billing.dto.BillingChargeStatusResponse;
import com.apipratudo.billing.model.BillingCharge;
import com.apipratudo.billing.repository.BillingChargeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class BillingService {

  private static final Logger log = LoggerFactory.getLogger(BillingService.class);

  private final BillingChargeRepository repository;
  private final PagBankClient pagBankClient;
  private final QuotaClient quotaClient;
  private final PagBankProperties pagBankProperties;
  private final WebhookProperties webhookProperties;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public BillingService(
      BillingChargeRepository repository,
      PagBankClient pagBankClient,
      QuotaClient quotaClient,
      PagBankProperties pagBankProperties,
      WebhookProperties webhookProperties,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.repository = repository;
    this.pagBankClient = pagBankClient;
    this.quotaClient = quotaClient;
    this.pagBankProperties = pagBankProperties;
    this.webhookProperties = webhookProperties;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public BillingChargeResponse createCharge(BillingChargeRequest request, String traceId) {
    String plan = normalizePlan(request.plan());
    String apiKeyHash = resolveHash(request.apiKey(), request.apiKeyHash());
    String apiKeyPrefix = apiKeyHash == null ? null : apiKeyHash.substring(0, Math.min(8, apiKeyHash.length()));

    String referenceId = "PIX-" + (apiKeyPrefix == null ? "key" : apiKeyPrefix) + "-" +
        UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    Map<String, Object> payload = buildPayload(request, plan, referenceId);

    JsonNode response = pagBankClient.createOrder(payload, UUID.randomUUID().toString());
    String chargeId = text(response, "id");
    if (!StringUtils.hasText(chargeId)) {
      throw new IllegalStateException("PagBank response missing id");
    }

    String statusTop = normalizeStatus(text(response, "status"));
    String createdAtRaw = text(response, "created_at");

    JsonNode qrCodes = response.path("qr_codes");
    if (!qrCodes.isArray() || qrCodes.isEmpty()) {
      throw new IllegalStateException("PagBank response missing qr_codes");
    }

    JsonNode qr = qrCodes.get(0);
    String pixCopyPaste = text(qr, "text");
    String expiresAtRaw = text(qr, "expiration_date");
    String qrBase64 = fetchQrBase64(qr);

    Instant createdAt = parseInstant(createdAtRaw, Instant.now(clock));
    Instant expiresAt = parseInstant(expiresAtRaw, null);
    Instant now = Instant.now(clock);

    BillingCharge charge = new BillingCharge(
        chargeId,
        referenceId,
        apiKeyHash,
        apiKeyPrefix,
        plan,
        request.amountCents(),
        request.description(),
        null,
        statusTop,
        Boolean.FALSE,
        createdAt,
        now,
        expiresAt,
        pixCopyPaste,
        qrBase64,
        null,
        Boolean.FALSE
    );

    repository.save(charge);

    return new BillingChargeResponse(
        chargeId,
        statusTop,
        expiresAtRaw,
        pixCopyPaste,
        qrBase64
    );
  }

  public BillingChargeStatusResponse getChargeStatus(String chargeId, String traceId) {
    BillingCharge existing = repository.findById(chargeId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Charge not found"));

    if (Boolean.TRUE.equals(existing.paid())) {
      return toStatusResponse(existing);
    }

    JsonNode response = pagBankClient.getOrder(chargeId);
    String statusTop = normalizeStatus(text(response, "status"));
    String statusCharge = extractChargeStatus(response);
    boolean paid = isPaid(statusTop) || isPaid(statusCharge);

    BillingCharge updated = merge(existing, new BillingCharge(
        existing.chargeId(),
        existing.referenceId(),
        existing.apiKeyHash(),
        existing.apiKeyPrefix(),
        existing.plan(),
        existing.amountCents(),
        existing.description(),
        statusCharge,
        statusTop,
        paid,
        existing.createdAt(),
        Instant.now(clock),
        existing.expiresAt(),
        existing.pixCopyPaste(),
        existing.qrCodeBase64(),
        paid ? Instant.now(clock) : existing.paidAt(),
        existing.premiumActivated()
    ));

    repository.save(updated);

    if (paid) {
      activatePremiumIfNeeded(updated, traceId);
      updated = repository.findById(chargeId).orElse(updated);
    }

    return toStatusResponse(updated);
  }

  public WebhookResult handleWebhook(
      byte[] rawBody,
      String contentType,
      String signature,
      String webhookSecret,
      String traceId
  ) {
    boolean secretOk = StringUtils.hasText(webhookProperties.getSecret())
        && StringUtils.hasText(webhookSecret)
        && webhookProperties.getSecret().equals(webhookSecret);

    if (isFormWebhook(contentType, signature)) {
      return new WebhookResult(true, HttpStatus.OK.value(), null, "form_ack", null);
    }

    boolean signatureOk = verifySignature(rawBody, signature);
    if (!signatureOk && !secretOk) {
      if (pagBankProperties.isWebhookStrict()) {
        return WebhookResult.unauthorized("Invalid x-authenticity-token signature");
      }
      return new WebhookResult(true, HttpStatus.OK.value(), null, "json_processed", "invalid_signature_logged");
    }

    JsonNode payload = parseJsonSafely(rawBody);
    if (payload == null) {
      return new WebhookResult(true, HttpStatus.OK.value(), null, "json_processed", "non_json_with_signature");
    }

    String chargeId = text(payload, "id");
    String referenceId = text(payload, "reference_id");
    String statusTop = normalizeStatus(text(payload, "status"));
    String statusCharge = extractChargeStatus(payload);
    boolean paid = isPaid(statusTop) || isPaid(statusCharge);

    if (StringUtils.hasText(chargeId)) {
      BillingCharge existing = repository.findById(chargeId).orElse(null);
      BillingCharge update = new BillingCharge(
          chargeId,
          referenceId,
          existing == null ? null : existing.apiKeyHash(),
          existing == null ? null : existing.apiKeyPrefix(),
          existing == null ? null : existing.plan(),
          existing == null ? null : existing.amountCents(),
          existing == null ? null : existing.description(),
          statusCharge,
          statusTop,
          paid,
          existing == null ? Instant.now(clock) : existing.createdAt(),
          Instant.now(clock),
          existing == null ? null : existing.expiresAt(),
          existing == null ? null : existing.pixCopyPaste(),
          existing == null ? null : existing.qrCodeBase64(),
          paid ? Instant.now(clock) : (existing == null ? null : existing.paidAt()),
          existing == null ? null : existing.premiumActivated()
      );

      BillingCharge merged = existing == null ? update : merge(existing, update);
      repository.save(merged);

      if (paid) {
        activatePremiumIfNeeded(merged, traceId);
      }
    }

    return new WebhookResult(true, HttpStatus.OK.value(), null, "json_processed", null);
  }

  public String resolveSignature(jakarta.servlet.http.HttpServletRequest request) {
    String signature = request.getHeader("x-authenticity-token");
    if (!StringUtils.hasText(signature)) {
      signature = request.getHeader("X-Authenticity-Token");
    }
    return signature == null ? "" : signature.trim();
  }

  private BillingChargeStatusResponse toStatusResponse(BillingCharge charge) {
    boolean paid = Boolean.TRUE.equals(charge.paid());
    boolean premium = Boolean.TRUE.equals(charge.premiumActivated());
    String status = StringUtils.hasText(charge.statusCharge()) ? charge.statusCharge() : charge.statusTop();
    return new BillingChargeStatusResponse(charge.chargeId(), status, paid, charge.plan(), premium);
  }

  private String normalizePlan(String plan) {
    if (!StringUtils.hasText(plan)) {
      throw new IllegalArgumentException("Missing plan");
    }
    return plan.trim().toUpperCase(Locale.ROOT);
  }

  private String resolveHash(String apiKey, String apiKeyHash) {
    if (StringUtils.hasText(apiKey)) {
      return HashingUtils.sha256Hex(apiKey.trim());
    }
    if (StringUtils.hasText(apiKeyHash)) {
      return apiKeyHash.trim();
    }
    throw new IllegalArgumentException("apiKey or apiKeyHash is required");
  }

  private Map<String, Object> buildPayload(BillingChargeRequest request, String plan, String referenceId) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("reference_id", referenceId);
    payload.put("customer", buildCustomer(request));
    payload.put("items", buildItems(request, plan));
    payload.put("qr_codes", buildQrCodes(request));

    if (StringUtils.hasText(pagBankProperties.getNotificationUrl())) {
      payload.put("notification_urls", new String[]{pagBankProperties.getNotificationUrl()});
    }

    Map<String, Object> shipping = buildShipping();
    if (shipping != null) {
      payload.put("shipping", shipping);
    }

    return payload;
  }

  private Map<String, Object> buildCustomer(BillingChargeRequest request) {
    PagBankProperties.Customer defaults = pagBankProperties.getCustomer();
    BillingChargeRequest.Payer payer = request.payer();

    String name = firstNonBlank(payer == null ? null : payer.name(), defaults.getName());
    String email = firstNonBlank(payer == null ? null : payer.email(), defaults.getEmail());
    String taxId = firstNonBlank(payer == null ? null : payer.taxId(), defaults.getTaxId());
    String phoneArea = firstNonBlank(payer == null ? null : payer.phoneArea(), defaults.getPhoneArea());
    String phoneNumber = firstNonBlank(payer == null ? null : payer.phoneNumber(), defaults.getPhoneNumber());

    Map<String, Object> phone = new HashMap<>();
    phone.put("type", "MOBILE");
    phone.put("country", "55");
    phone.put("area", phoneArea);
    phone.put("number", phoneNumber);

    Map<String, Object> customer = new HashMap<>();
    customer.put("name", name);
    customer.put("email", email);
    customer.put("tax_id", taxId);
    customer.put("phones", new Map[]{phone});
    return customer;
  }

  private Map<String, Object>[] buildItems(BillingChargeRequest request, String plan) {
    String name = StringUtils.hasText(request.description())
        ? request.description()
        : "Plano " + plan;

    Map<String, Object> item = new HashMap<>();
    item.put("name", name);
    item.put("quantity", 1);
    item.put("unit_amount", request.amountCents());
    return new Map[]{item};
  }

  private Map<String, Object>[] buildQrCodes(BillingChargeRequest request) {
    Map<String, Object> amount = new HashMap<>();
    amount.put("value", request.amountCents());

    Map<String, Object> qr = new HashMap<>();
    qr.put("amount", amount);
    qr.put("expiration_date", qrExpirationIso());

    return new Map[]{qr};
  }

  private Map<String, Object> buildShipping() {
    PagBankProperties.Shipping shipping = pagBankProperties.getShipping();
    if (!StringUtils.hasText(shipping.getPostalCode())) {
      return null;
    }

    Map<String, Object> address = new HashMap<>();
    address.put("street", shipping.getStreet());
    address.put("number", shipping.getNumber());
    address.put("locality", shipping.getLocality());
    address.put("city", shipping.getCity());
    address.put("region_code", shipping.getRegion());
    address.put("country", shipping.getCountry());
    address.put("postal_code", shipping.getPostalCode());

    Map<String, Object> result = new HashMap<>();
    result.put("address", address);
    return result;
  }

  private String qrExpirationIso() {
    int ttl = Math.max(30, pagBankProperties.getQrTtlSeconds());
    ZoneId zone = ZoneId.of(pagBankProperties.getTimezone());
    ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
    ZonedDateTime exp = now.plusSeconds(ttl).withNano(0);
    return exp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  private String fetchQrBase64(JsonNode qr) {
    JsonNode links = qr.path("links");
    if (!links.isArray()) {
      return "";
    }
    for (JsonNode link : links) {
      String rel = text(link, "rel");
      if (rel != null && rel.equalsIgnoreCase("QRCODE.BASE64")) {
        String href = text(link, "href");
        return pagBankClient.fetchQrCodeBase64(href);
      }
    }
    return "";
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

  private Instant parseInstant(String value, Instant fallback) {
    if (!StringUtils.hasText(value)) {
      return fallback;
    }
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (Exception ex) {
      return fallback;
    }
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

  private boolean isPaid(String status) {
    if (!StringUtils.hasText(status)) {
      return false;
    }
    String normalized = status.trim().toUpperCase(Locale.ROOT);
    return "PAID".equals(normalized) || "CONFIRMED".equals(normalized);
  }

  private BillingCharge merge(BillingCharge existing, BillingCharge update) {
    return new BillingCharge(
        existing.chargeId(),
        firstNonBlank(update.referenceId(), existing.referenceId()),
        firstNonBlank(update.apiKeyHash(), existing.apiKeyHash()),
        firstNonBlank(update.apiKeyPrefix(), existing.apiKeyPrefix()),
        firstNonBlank(update.plan(), existing.plan()),
        update.amountCents() != null ? update.amountCents() : existing.amountCents(),
        firstNonBlank(update.description(), existing.description()),
        firstNonBlank(update.statusCharge(), existing.statusCharge()),
        firstNonBlank(update.statusTop(), existing.statusTop()),
        Boolean.TRUE.equals(update.paid()) || Boolean.TRUE.equals(existing.paid()),
        update.createdAt() != null ? update.createdAt() : existing.createdAt(),
        update.updatedAt() != null ? update.updatedAt() : existing.updatedAt(),
        update.expiresAt() != null ? update.expiresAt() : existing.expiresAt(),
        firstNonBlank(update.pixCopyPaste(), existing.pixCopyPaste()),
        firstNonBlank(update.qrCodeBase64(), existing.qrCodeBase64()),
        update.paidAt() != null ? update.paidAt() : existing.paidAt(),
        Boolean.TRUE.equals(update.premiumActivated()) || Boolean.TRUE.equals(existing.premiumActivated())
    );
  }

  private String firstNonBlank(String primary, String fallback) {
    if (StringUtils.hasText(primary)) {
      return primary;
    }
    return fallback;
  }

  private void activatePremiumIfNeeded(BillingCharge charge, String traceId) {
    if (!Boolean.TRUE.equals(charge.paid())) {
      return;
    }
    if (Boolean.TRUE.equals(charge.premiumActivated())) {
      return;
    }
    if (!StringUtils.hasText(charge.apiKeyHash())) {
      log.warn("Skipping premium activation: missing apiKeyHash chargeId={}", charge.chargeId());
      return;
    }

    try {
      quotaClient.activatePremium(charge.apiKeyHash(), charge.plan(), traceId);
      BillingCharge updated = new BillingCharge(
          charge.chargeId(),
          charge.referenceId(),
          charge.apiKeyHash(),
          charge.apiKeyPrefix(),
          charge.plan(),
          charge.amountCents(),
          charge.description(),
          charge.statusCharge(),
          charge.statusTop(),
          charge.paid(),
          charge.createdAt(),
          Instant.now(clock),
          charge.expiresAt(),
          charge.pixCopyPaste(),
          charge.qrCodeBase64(),
          charge.paidAt(),
          Boolean.TRUE
      );
      repository.save(updated);
    } catch (Exception ex) {
      log.warn("Failed to activate premium chargeId={} reason={}", charge.chargeId(), ex.getMessage());
    }
  }

  private boolean isFormWebhook(String contentType, String signature) {
    String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    return normalized.contains("application/x-www-form-urlencoded");
  }

  private boolean verifySignature(byte[] rawBody, String receivedSignature) {
    String token = resolveWebhookToken();
    if (!StringUtils.hasText(token)) {
      return false;
    }
    byte[] prefix = (token + "-").getBytes(StandardCharsets.UTF_8);
    byte[] combined = new byte[prefix.length + rawBody.length];
    System.arraycopy(prefix, 0, combined, 0, prefix.length);
    System.arraycopy(rawBody, 0, combined, prefix.length, rawBody.length);
    String expected = sha256Hex(combined);
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        (receivedSignature == null ? "" : receivedSignature.trim()).getBytes(StandardCharsets.UTF_8)
    );
  }

  private String resolveWebhookToken() {
    if (StringUtils.hasText(pagBankProperties.getWebhookToken())) {
      return pagBankProperties.getWebhookToken().trim();
    }
    if (StringUtils.hasText(pagBankProperties.getToken())) {
      return pagBankProperties.getToken().trim();
    }
    return null;
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

  public record WebhookResult(boolean ok, int status, String message, String mode, String warning) {
    static WebhookResult unauthorized(String message) {
      return new WebhookResult(false, HttpStatus.UNAUTHORIZED.value(), message, null, null);
    }
  }
}
