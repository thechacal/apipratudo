package com.apipratudo.billingsaas.dto;

import java.time.LocalDate;

public class ReportSummaryResponse {

  private LocalDate from;
  private LocalDate to;
  private long countTotal;
  private long countPaid;
  private long countPending;
  private long totalCents;
  private long paidCents;
  private long pendingCents;

  public LocalDate getFrom() {
    return from;
  }

  public void setFrom(LocalDate from) {
    this.from = from;
  }

  public LocalDate getTo() {
    return to;
  }

  public void setTo(LocalDate to) {
    this.to = to;
  }

  public long getCountTotal() {
    return countTotal;
  }

  public void setCountTotal(long countTotal) {
    this.countTotal = countTotal;
  }

  public long getCountPaid() {
    return countPaid;
  }

  public void setCountPaid(long countPaid) {
    this.countPaid = countPaid;
  }

  public long getCountPending() {
    return countPending;
  }

  public void setCountPending(long countPending) {
    this.countPending = countPending;
  }

  public long getTotalCents() {
    return totalCents;
  }

  public void setTotalCents(long totalCents) {
    this.totalCents = totalCents;
  }

  public long getPaidCents() {
    return paidCents;
  }

  public void setPaidCents(long paidCents) {
    this.paidCents = paidCents;
  }

  public long getPendingCents() {
    return pendingCents;
  }

  public void setPendingCents(long pendingCents) {
    this.pendingCents = pendingCents;
  }
}
