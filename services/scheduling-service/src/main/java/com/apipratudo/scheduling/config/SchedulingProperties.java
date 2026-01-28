package com.apipratudo.scheduling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scheduling")
public class SchedulingProperties {

  private String dayStart = "08:00";
  private String dayEnd = "18:00";
  private int slotStepMin = 15;
  private int holdTtlMin = 15;
  private String timezone = "America/Sao_Paulo";

  public String getDayStart() {
    return dayStart;
  }

  public void setDayStart(String dayStart) {
    this.dayStart = dayStart;
  }

  public String getDayEnd() {
    return dayEnd;
  }

  public void setDayEnd(String dayEnd) {
    this.dayEnd = dayEnd;
  }

  public int getSlotStepMin() {
    return slotStepMin;
  }

  public void setSlotStepMin(int slotStepMin) {
    this.slotStepMin = slotStepMin;
  }

  public int getHoldTtlMin() {
    return holdTtlMin;
  }

  public void setHoldTtlMin(int holdTtlMin) {
    this.holdTtlMin = holdTtlMin;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }
}
