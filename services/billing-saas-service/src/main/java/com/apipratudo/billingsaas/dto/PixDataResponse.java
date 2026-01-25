package com.apipratudo.billingsaas.dto;

import java.time.Instant;

public class PixDataResponse {

  private String provider;
  private String providerChargeId;
  private String txid;
  private String pixCopyPaste;
  private String qrCodeBase64;
  private Instant expiresAt;

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getProviderChargeId() {
    return providerChargeId;
  }

  public void setProviderChargeId(String providerChargeId) {
    this.providerChargeId = providerChargeId;
  }

  public String getTxid() {
    return txid;
  }

  public void setTxid(String txid) {
    this.txid = txid;
  }

  public String getPixCopyPaste() {
    return pixCopyPaste;
  }

  public void setPixCopyPaste(String pixCopyPaste) {
    this.pixCopyPaste = pixCopyPaste;
  }

  public String getQrCodeBase64() {
    return qrCodeBase64;
  }

  public void setQrCodeBase64(String qrCodeBase64) {
    this.qrCodeBase64 = qrCodeBase64;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}
