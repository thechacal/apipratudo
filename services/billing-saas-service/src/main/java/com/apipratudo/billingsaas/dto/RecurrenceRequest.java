package com.apipratudo.billingsaas.dto;

import com.apipratudo.billingsaas.model.RecurrenceFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RecurrenceRequest {

  @NotNull
  private RecurrenceFrequency frequency;

  @Min(1)
  private Integer interval;

  @Min(1)
  @Max(31)
  private Integer dayOfMonth;

  public RecurrenceFrequency getFrequency() {
    return frequency;
  }

  public void setFrequency(RecurrenceFrequency frequency) {
    this.frequency = frequency;
  }

  public Integer getInterval() {
    return interval;
  }

  public void setInterval(Integer interval) {
    this.interval = interval;
  }

  public Integer getDayOfMonth() {
    return dayOfMonth;
  }

  public void setDayOfMonth(Integer dayOfMonth) {
    this.dayOfMonth = dayOfMonth;
  }
}
