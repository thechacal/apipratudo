package com.apipratudo.billingsaas.service;

import com.apipratudo.billingsaas.crypto.CryptoService;
import com.apipratudo.billingsaas.dto.PagbankConnectRequest;
import com.apipratudo.billingsaas.dto.PagbankStatusResponse;
import com.apipratudo.billingsaas.idempotency.HashingUtils;
import com.apipratudo.billingsaas.model.PagbankEnvironment;
import com.apipratudo.billingsaas.model.PagbankProviderConfig;
import com.apipratudo.billingsaas.model.EncryptedValue;
import com.apipratudo.billingsaas.repository.PagbankProviderConfigStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PagbankProviderService {

  private final PagbankProviderConfigStore store;
  private final CryptoService cryptoService;
  private final Clock clock;

  public PagbankProviderService(
      PagbankProviderConfigStore store,
      CryptoService cryptoService,
      Clock clock
  ) {
    this.store = store;
    this.cryptoService = cryptoService;
    this.clock = clock;
  }

  public PagbankProviderConfig connect(String tenantId, PagbankConnectRequest request) {
    PagbankEnvironment environment = PagbankEnvironment.fromString(request.getEnvironment());
    String token = request.getToken().trim();
    String webhookToken = StringUtils.hasText(request.getWebhookToken())
        ? request.getWebhookToken().trim()
        : null;

    EncryptedValue tokenEncrypted = cryptoService.encrypt(token, aad(tenantId));
    EncryptedValue webhookEncrypted = webhookToken == null ? null : cryptoService.encrypt(webhookToken, aad(tenantId));
    String fingerprint = fingerprint(token);
    Instant now = Instant.now(clock);

    PagbankProviderConfig config = new PagbankProviderConfig(
        tenantId,
        true,
        environment,
        tokenEncrypted,
        webhookEncrypted,
        fingerprint,
        now,
        now,
        null
    );

    return store.save(tenantId, config);
  }

  public PagbankStatusResponse status(String tenantId) {
    Optional<PagbankProviderConfig> existing = store.find(tenantId);
    PagbankStatusResponse response = new PagbankStatusResponse();
    if (existing.isEmpty()) {
      response.setConnected(false);
      return response;
    }
    PagbankProviderConfig config = existing.get();
    response.setConnected(config.enabled());
    response.setEnvironment(config.environment() == null ? null : config.environment().name());
    response.setLastVerifiedAt(config.lastVerifiedAt());
    response.setFingerprint(config.fingerprint());
    return response;
  }

  public void disconnect(String tenantId) {
    store.delete(tenantId);
  }

  public PagbankProviderConfig requireConfig(String tenantId) {
    PagbankProviderConfig config = store.find(tenantId)
        .orElseThrow(() -> new IllegalStateException("PagBank not connected"));
    if (!config.enabled()) {
      throw new IllegalStateException("PagBank not enabled");
    }
    return config;
  }

  public String decryptToken(PagbankProviderConfig config) {
    return cryptoService.decrypt(config.token(), aad(config.tenantId()));
  }

  public String resolveWebhookToken(PagbankProviderConfig config) {
    if (config.webhookToken() != null) {
      String webhookToken = cryptoService.decrypt(config.webhookToken(), aad(config.tenantId()));
      if (StringUtils.hasText(webhookToken)) {
        return webhookToken;
      }
    }
    return decryptToken(config);
  }

  private String fingerprint(String token) {
    String hash = HashingUtils.sha256Hex(token);
    return hash.length() <= 8 ? hash : hash.substring(0, 8);
  }

  private String aad(String tenantId) {
    return tenantId + ":PAGBANK";
  }
}
