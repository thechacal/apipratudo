package com.apipratudo.billingsaas.provider;

import com.apipratudo.billingsaas.model.Charge;
import com.apipratudo.billingsaas.model.Customer;
import com.apipratudo.billingsaas.model.PixData;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FakePixProvider implements PixProvider {

  private final Clock clock;

  public FakePixProvider(Clock clock) {
    this.clock = clock;
  }

  @Override
  public PixData generatePix(String tenantId, Charge charge, Customer customer, long expiresInSeconds) {
    Instant now = Instant.now(clock);
    long safeTtl = expiresInSeconds > 0 ? expiresInSeconds : 3600;
    Instant expiresAt = now.plusSeconds(safeTtl);
    String providerChargeId = "fake_" + UUID.randomUUID().toString().replace("-", "");
    String txid = "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    String pixCopyPaste = "FAKEPIX|" + providerChargeId + "|" + charge.amountCents();
    String qrCodeBase64 = Base64.getEncoder().encodeToString(pixCopyPaste.getBytes(StandardCharsets.UTF_8));

    return new PixData(
        providerName(),
        providerChargeId,
        txid,
        pixCopyPaste,
        qrCodeBase64,
        expiresAt
    );
  }

  @Override
  public String providerName() {
    return "FAKE";
  }
}
