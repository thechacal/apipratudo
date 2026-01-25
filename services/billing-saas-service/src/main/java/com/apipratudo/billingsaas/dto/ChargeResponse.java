package com.apipratudo.billingsaas.dto;

import com.apipratudo.billingsaas.model.ChargeStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public class ChargeResponse {

  private String id;
  private String customerId;
  private long amountCents;
  private String currency;
  private String description;
  private LocalDate dueDate;
  private RecurrenceRequest recurrence;
  private Map<String, String> metadata;
  private ChargeStatus status;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant paidAt;
  private PixDataResponse pix;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String customerId) {
    this.customerId = customerId;
  }

  public long getAmountCents() {
    return amountCents;
  }

  public void setAmountCents(long amountCents) {
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

  public ChargeStatus getStatus() {
    return status;
  }

  public void setStatus(ChargeStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(Instant paidAt) {
    this.paidAt = paidAt;
  }

  public PixDataResponse getPix() {
    return pix;
  }

  public void setPix(PixDataResponse pix) {
    this.pix = pix;
  }
}
