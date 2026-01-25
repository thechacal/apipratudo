package com.apipratudo.gateway.billingsaas.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RecurrenceRequest {

  @NotBlank
  @Pattern(regexp = "MONTHLY")
  private String frequency;

  @Min(1)
  private Integer interval;

  @Min(1)
  @Max(31)
  private Integer dayOfMonth;

  public String getFrequency() {
    return frequency;
  }

  public void setFrequency(String frequency) {
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
