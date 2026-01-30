package com.apipratudo.scheduling.model;

import java.time.Instant;

public record Agenda(
    String id,
    String tenantId,
    String name,
    String timezone,
    String workingHoursStart,
    String workingHoursEnd,
    int slotStepMin,
    long noShowFeeCents,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
  public Agenda withUpdates(
      String name,
      String timezone,
      String workingHoursStart,
      String workingHoursEnd,
      Integer slotStepMin,
      Long noShowFeeCents,
      Boolean active,
      Instant now
  ) {
    return new Agenda(
        id,
        tenantId,
        name == null ? this.name : name,
        timezone == null ? this.timezone : timezone,
        workingHoursStart == null ? this.workingHoursStart : workingHoursStart,
        workingHoursEnd == null ? this.workingHoursEnd : workingHoursEnd,
        slotStepMin == null ? this.slotStepMin : slotStepMin,
        noShowFeeCents == null ? this.noShowFeeCents : noShowFeeCents,
        active == null ? this.active : active,
        createdAt,
        now
    );
  }
}
