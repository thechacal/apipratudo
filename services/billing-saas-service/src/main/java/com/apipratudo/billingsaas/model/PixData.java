package com.apipratudo.billingsaas.model;

import java.time.Instant;

public record PixData(
    String provider,
    String providerChargeId,
    String txid,
    String pixCopyPaste,
    String qrCodeBase64,
    Instant expiresAt
) {
}
