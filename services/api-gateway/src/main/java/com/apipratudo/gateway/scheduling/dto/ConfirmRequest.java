package com.apipratudo.gateway.scheduling.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfirmRequest {

  @NotBlank
  private String appointmentId;

  public String getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(String appointmentId) {
    this.appointmentId = appointmentId;
  }
}
