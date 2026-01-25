package com.apipratudo.billingsaas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.Map;

public class ChargeCreateRequest {

  @NotBlank
  private String customerId;

  @NotNull
  @Positive
  private Long amountCents;

  private String currency;

  private String description;

  @NotNull
  private LocalDate dueDate;

  @Valid
  private RecurrenceRequest recurrence;

  private Map<String, String> metadata;

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String customerId) {
    this.customerId = customerId;
  }

  public Long getAmountCents() {
    return amountCents;
  }

  public void setAmountCents(Long amountCents) {
    this.amountCents = amountCents;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
  }

  public RecurrenceRequest getRecurrence() {
    return recurrence;
  }

  public void setRecurrence(RecurrenceRequest recurrence) {
    this.recurrence = recurrence;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }
}
