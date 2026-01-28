package com.apipratudo.gateway.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ServiceCreateRequest {

  @NotBlank
  private String name;

  @Min(1)
  private int durationMin;

  @Min(0)
  private int prepMin;

  @Min(0)
  private int bufferMin;

  @Min(0)
  private long noShowFeeCents;

  @NotNull
  private Boolean active;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getDurationMin() {
    return durationMin;
  }

  public void setDurationMin(int durationMin) {
    this.durationMin = durationMin;
  }

  public int getPrepMin() {
    return prepMin;
  }

  public void setPrepMin(int prepMin) {
    this.prepMin = prepMin;
  }

  public int getBufferMin() {
    return bufferMin;
  }

  public void setBufferMin(int bufferMin) {
    this.bufferMin = bufferMin;
  }

  public long getNoShowFeeCents() {
    return noShowFeeCents;
  }

  public void setNoShowFeeCents(long noShowFeeCents) {
    this.noShowFeeCents = noShowFeeCents;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }
}
