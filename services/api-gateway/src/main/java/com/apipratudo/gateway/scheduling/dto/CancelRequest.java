package com.apipratudo.gateway.scheduling.dto;

import jakarta.validation.constraints.NotBlank;

public class CancelRequest {

  @NotBlank
  private String appointmentId;

  private String reason;

  public String getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(String appointmentId) {
    this.appointmentId = appointmentId;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}
