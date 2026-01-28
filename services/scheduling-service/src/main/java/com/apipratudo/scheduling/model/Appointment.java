package com.apipratudo.scheduling.model;

import java.time.Instant;

public record Appointment(
    String id,
    String tenantId,
    String serviceId,
    String agendaId,
    Instant startAt,
    Instant endAt,
    AppointmentStatus status,
    Instant holdExpiresAt,
    Customer customer,
    String notes,
    long noShowFeeCents,
    Instant createdAt,
    Instant updatedAt,
    Instant lastNotifiedAt,
    String lastNotificationType
) {

  public Appointment withStatus(AppointmentStatus newStatus, Instant now) {
    return new Appointment(
        id,
        tenantId,
        serviceId,
        agendaId,
        startAt,
        endAt,
        newStatus,
        holdExpiresAt,
        customer,
        notes,
        noShowFeeCents,
        createdAt,
        now,
        lastNotifiedAt,
        lastNotificationType
    );
  }

  public Appointment withNotification(String type, Instant notifiedAt, Instant now) {
    return new Appointment(
        id,
        tenantId,
        serviceId,
        agendaId,
        startAt,
        endAt,
        status,
        holdExpiresAt,
        customer,
        notes,
        noShowFeeCents,
        createdAt,
        now,
        notifiedAt,
        type
    );
  }
}
