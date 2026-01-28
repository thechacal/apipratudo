package com.apipratudo.gateway.scheduling.dto;

import jakarta.validation.constraints.NotBlank;

public class NotifyRequest {

  @NotBlank
  private String appointmentId;

  @NotBlank
  private String type;

  public String getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(String appointmentId) {
    this.appointmentId = appointmentId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }
}
