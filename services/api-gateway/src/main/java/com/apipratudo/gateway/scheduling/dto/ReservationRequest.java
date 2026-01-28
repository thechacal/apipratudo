package com.apipratudo.gateway.scheduling.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class ReservationRequest {

  @NotBlank
  private String serviceId;

  @NotBlank
  private String agendaId;

  @NotNull
  private Instant startAt;

  @NotNull
  @Valid
  private CustomerRequest customer;

  private String notes;

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public String getAgendaId() {
    return agendaId;
  }

  public void setAgendaId(String agendaId) {
    this.agendaId = agendaId;
  }

  public Instant getStartAt() {
    return startAt;
  }

  public void setStartAt(Instant startAt) {
    this.startAt = startAt;
  }

  public CustomerRequest getCustomer() {
    return customer;
  }

  public void setCustomer(CustomerRequest customer) {
    this.customer = customer;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }
}
