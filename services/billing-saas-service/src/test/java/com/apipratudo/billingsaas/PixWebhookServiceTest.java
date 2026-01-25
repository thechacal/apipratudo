package com.apipratudo.billingsaas;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.billingsaas.dto.PagbankConnectRequest;
import com.apipratudo.billingsaas.model.Charge;
import com.apipratudo.billingsaas.model.ChargeStatus;
import com.apipratudo.billingsaas.model.PixData;
import com.apipratudo.billingsaas.model.PixProviderIndex;
import com.apipratudo.billingsaas.repository.ChargeStore;
import com.apipratudo.billingsaas.repository.PixProviderIndexStore;
import com.apipratudo.billingsaas.service.PagbankProviderService;
import com.apipratudo.billingsaas.service.PixWebhookService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PixWebhookServiceTest {

  @Autowired
  private PixWebhookService pixWebhookService;

  @Autowired
  private ChargeStore chargeStore;

  @Autowired
  private PixProviderIndexStore pixProviderIndexStore;

  @Autowired
  private PagbankProviderService pagbankProviderService;

  @Test
  void pagbankWebhookValidSignatureMarksPaid() {
    String tenantId = "tenant-pagbank";
    String token = "token-123";

    PagbankConnectRequest connect = new PagbankConnectRequest();
    connect.setToken(token);
    connect.setEnvironment("SANDBOX");
    pagbankProviderService.connect(tenantId, connect);

    String chargeId = "chg-1";
    String providerChargeId = "order-123";
    PixData pixData = new PixData("PAGBANK", providerChargeId, "tx-1", "copia", "qr", Instant.now());
    Charge charge = new Charge(
        chargeId,
        "cus-1",
        1990,
        "BRL",
        "Assinatura",
        null,
        null,
        Map.of(),
        ChargeStatus.PIX_GENERATED,
        Instant.now(),
        Instant.now(),
        null,
        pixData,
        providerChargeId,
        tenantId
    );
    chargeStore.save(tenantId, charge);
    pixProviderIndexStore.save(new PixProviderIndex("PAGBANK", providerChargeId, tenantId, chargeId, Instant.now()));

    String payload = "{\"id\":\"" + providerChargeId + "\",\"status\":\"PAID\"}";
    byte[] raw = payload.getBytes(StandardCharsets.UTF_8);
    String signature = signature(token, raw);

    PixWebhookService.WebhookResult result = pixWebhookService.handle(raw, "application/json", signature);
    assertThat(result.ok()).isTrue();

    Charge updated = chargeStore.findById(tenantId, chargeId).orElseThrow();
    assertThat(updated.status()).isEqualTo(ChargeStatus.PAID);
  }

  @Test
  void pagbankWebhookInvalidSignatureReturns401() {
    String tenantId = "tenant-invalid";
    String token = "token-xyz";

    PagbankConnectRequest connect = new PagbankConnectRequest();
    connect.setToken(token);
    connect.setEnvironment("SANDBOX");
    pagbankProviderService.connect(tenantId, connect);

    String chargeId = "chg-2";
    String providerChargeId = "order-999";
    PixData pixData = new PixData("PAGBANK", providerChargeId, "tx-2", "copia", "qr", Instant.now());
    Charge charge = new Charge(
        chargeId,
        "cus-2",
        1000,
        "BRL",
        "Teste",
        null,
        null,
        Map.of(),
        ChargeStatus.PIX_GENERATED,
        Instant.now(),
        Instant.now(),
        null,
        pixData,
        providerChargeId,
        tenantId
    );
    chargeStore.save(tenantId, charge);
    pixProviderIndexStore.save(new PixProviderIndex("PAGBANK", providerChargeId, tenantId, chargeId, Instant.now()));

    String payload = "{\"id\":\"" + providerChargeId + "\",\"status\":\"PAID\"}";
    byte[] raw = payload.getBytes(StandardCharsets.UTF_8);

    PixWebhookService.WebhookResult result = pixWebhookService.handle(raw, "application/json", "invalid");
    assertThat(result.ok()).isFalse();
    assertThat(result.status()).isEqualTo(401);
  }

  private String signature(String token, byte[] raw) {
    byte[] prefix = (token + "-").getBytes(StandardCharsets.UTF_8);
    byte[] combined = new byte[prefix.length + raw.length];
    System.arraycopy(prefix, 0, combined, 0, prefix.length);
    System.arraycopy(raw, 0, combined, prefix.length, raw.length);
    return sha256Hex(combined);
  }

  private String sha256Hex(byte[] data) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
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
}
