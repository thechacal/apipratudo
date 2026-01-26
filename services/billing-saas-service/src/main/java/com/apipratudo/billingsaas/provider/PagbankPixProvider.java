package com.apipratudo.billingsaas.provider;

import com.apipratudo.billingsaas.config.PagBankProperties;
import com.apipratudo.billingsaas.crypto.CryptoService;
import com.apipratudo.billingsaas.error.ConfigurationException;
import com.apipratudo.billingsaas.idempotency.HashingUtils;
import com.apipratudo.billingsaas.model.Charge;
import com.apipratudo.billingsaas.model.Customer;
import com.apipratudo.billingsaas.model.PagbankEnvironment;
import com.apipratudo.billingsaas.model.PagbankProviderConfig;
import com.apipratudo.billingsaas.model.PixData;
import com.apipratudo.billingsaas.repository.PagbankProviderConfigStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PagbankPixProvider implements PixProvider {

  private static final Logger log = LoggerFactory.getLogger(PagbankPixProvider.class);

  private final WebClient.Builder webClientBuilder;
  private final ObjectMapper objectMapper;
  private final PagBankProperties properties;
  private final PagbankProviderConfigStore configStore;
  private final CryptoService cryptoService;
  private final Clock clock;

  public PagbankPixProvider(
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper,
      PagBankProperties properties,
      PagbankProviderConfigStore configStore,
      CryptoService cryptoService,
      Clock clock
  ) {
    this.webClientBuilder = webClientBuilder;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.configStore = configStore;
    this.cryptoService = cryptoService;
    this.clock = clock;
  }

  @Override
  public PixData generatePix(String tenantId, Charge charge, Customer customer, long expiresInSeconds) {
    PagbankProviderConfig config = configStore.find(tenantId)
        .filter(PagbankProviderConfig::enabled)
        .orElseThrow(() -> new IllegalStateException("PagBank not connected"));

    String token = cryptoService.decrypt(config.token(), aad(tenantId));
    PagbankClient client = new PagbankClient(
        webClientBuilder,
        objectMapper,
        resolveBaseUrl(config.environment()),
        token,
        properties.getTimeoutMs()
    );

    String notificationUrl = resolveNotificationUrl(config.environment());
    Map<String, Object> payload = buildPayload(charge, customer, expiresInSeconds, notificationUrl);
    String idempotencyKey = "pix-" + HashingUtils.sha256Hex(charge.id()).substring(0, 12);
    JsonNode response = client.createOrder(payload, idempotencyKey);

    String providerChargeId = text(response, "id");
    if (!StringUtils.hasText(providerChargeId)) {
      throw new IllegalStateException("PagBank response missing id");
    }

    JsonNode qrCodes = response.path("qr_codes");
    if (!qrCodes.isArray() || qrCodes.isEmpty()) {
      throw new IllegalStateException("PagBank response missing qr_codes");
    }

    JsonNode qr = qrCodes.get(0);
    String pixCopyPaste = text(qr, "text");
    String expiresAtRaw = text(qr, "expiration_date");
    String qrBase64 = fetchQrBase64(client, qr);
    Instant expiresAt = parseInstant(expiresAtRaw, null);
    String txid = firstNonBlank(text(qr, "id"), providerChargeId);

    log.info("PagBank PIX created tenant={} chargeId={} providerChargeId={} expiresAt={}",
        tenantId, charge.id(), providerChargeId, expiresAtRaw);

    return new PixData(
        providerName(),
        providerChargeId,
        txid,
        pixCopyPaste,
        qrBase64,
        expiresAt
    );
  }

  @Override
  public String providerName() {
    return "PAGBANK";
  }

  private Map<String, Object> buildPayload(
      Charge charge,
      Customer customer,
      long expiresInSeconds,
      String notificationUrl
  ) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("reference_id", charge.id());
    payload.put("customer", buildCustomer(customer));
    payload.put("items", buildItems(charge));
    payload.put("qr_codes", buildQrCodes(charge, expiresInSeconds));

    if (StringUtils.hasText(notificationUrl)) {
      payload.put("notification_urls", new String[]{notificationUrl});
    }

    return payload;
  }

  private Map<String, Object> buildCustomer(Customer customer) {
    Map<String, Object> customerData = new HashMap<>();
    customerData.put("name", safe(customer.name(), "Cliente"));
    customerData.put("email", safe(customer.email(), "cliente@exemplo.com"));
    customerData.put("tax_id", digitsOnly(customer.document()));

    String[] phoneParts = splitPhone(customer.phone());
    Map<String, Object> phone = new HashMap<>();
    phone.put("type", "MOBILE");
    phone.put("country", "55");
    phone.put("area", phoneParts[0]);
    phone.put("number", phoneParts[1]);
    customerData.put("phones", new Map[]{phone});
    return customerData;
  }

  private Map<String, Object>[] buildItems(Charge charge) {
    String name = StringUtils.hasText(charge.description())
        ? charge.description()
        : "Cobranca " + charge.id();
    Map<String, Object> item = new HashMap<>();
    item.put("name", name);
    item.put("quantity", 1);
    item.put("unit_amount", charge.amountCents());
    return new Map[]{item};
  }

  private Map<String, Object>[] buildQrCodes(Charge charge, long expiresInSeconds) {
    Map<String, Object> amount = new HashMap<>();
    amount.put("value", charge.amountCents());

    Map<String, Object> qr = new HashMap<>();
    qr.put("amount", amount);
    qr.put("expiration_date", qrExpirationIso(expiresInSeconds));

    return new Map[]{qr};
  }

  private String qrExpirationIso(long expiresInSeconds) {
    int ttl = expiresInSeconds > 0 ? (int) expiresInSeconds : properties.getQrTtlSeconds();
    ttl = Math.max(1800, ttl);
    ZoneId zone = ZoneId.of(properties.getTimezone());
    ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
    ZonedDateTime exp = now.plusSeconds(ttl).withNano(0);
    return exp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  private String fetchQrBase64(PagbankClient client, JsonNode qr) {
    JsonNode links = qr.path("links");
    if (!links.isArray()) {
      return "";
    }
    for (JsonNode link : links) {
      String rel = text(link, "rel");
      if (rel != null && rel.equalsIgnoreCase("QRCODE.BASE64")) {
        String href = text(link, "href");
        return client.fetchQrCodeBase64(href);
      }
    }
    return "";
  }

  private String resolveBaseUrl(PagbankEnvironment environment) {
    return environment == PagbankEnvironment.PRODUCTION
        ? properties.getProductionBaseUrl()
        : properties.getSandboxBaseUrl();
  }

  private String resolveNotificationUrl(PagbankEnvironment environment) {
    String notificationUrl = properties.getNotificationUrl();
    if (StringUtils.hasText(notificationUrl)) {
      return notificationUrl.trim();
    }
    if (environment == PagbankEnvironment.PRODUCTION) {
      throw new ConfigurationException("BILLING_SAAS_PAGBANK_NOTIFICATION_URL ausente");
    }
    log.warn("PagBank notification URL not configured; webhook may not be delivered. " +
        "Set BILLING_SAAS_PAGBANK_NOTIFICATION_URL.");
    return null;
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

  private String safe(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
  }

  private String digitsOnly(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.replaceAll("\\D", "");
  }

  private String[] splitPhone(String phone) {
    String digits = digitsOnly(phone);
    if (digits.length() >= 11) {
      digits = digits.substring(digits.length() - 11);
    }
    if (digits.length() < 10) {
      return new String[]{"11", "999999999"};
    }
    String area = digits.substring(0, 2);
    String number = digits.substring(2);
    return new String[]{area, number};
  }

  private String firstNonBlank(String primary, String fallback) {
    if (StringUtils.hasText(primary)) {
      return primary;
    }
    return fallback;
  }

  private String aad(String tenantId) {
    return tenantId + ":PAGBANK";
  }
}
