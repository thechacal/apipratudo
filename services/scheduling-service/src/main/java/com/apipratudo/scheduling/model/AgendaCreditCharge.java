package com.apipratudo.scheduling.model;

import java.time.Instant;

public record AgendaCreditCharge(
    String chargeId,
    String tenantId,
    String agendaId,
    long creditsAdded,
    long amountCents,
    String status,
    String providerChargeId,
    String pixCopyPaste,
    Instant pixExpiresAt,
    Instant createdAt,
    Instant updatedAt,
    Instant appliedAt
) {
  public AgendaCreditCharge withStatus(String status, Instant now) {
    return new AgendaCreditCharge(
        chargeId,
        tenantId,
        agendaId,
        creditsAdded,
        amountCents,
        status,
        providerChargeId,
        pixCopyPaste,
        pixExpiresAt,
        createdAt,
        now,
        appliedAt
    );
  }

  public AgendaCreditCharge withApplied(Instant now) {
    return new AgendaCreditCharge(
        chargeId,
        tenantId,
        agendaId,
        creditsAdded,
        amountCents,
        status,
        providerChargeId,
        pixCopyPaste,
        pixExpiresAt,
        createdAt,
        updatedAt,
        now
    );
  }
}
