package com.apipratudo.scheduling.dto;

import java.time.Instant;

public record AgendaCreditsUpgradeResponse(
    String agendaId,
    String chargeId,
    String status,
    long creditsAdded,
    long creditsBalance,
    String providerChargeId,
    String pixCopyPaste,
    Instant expiresAt
) {
}
