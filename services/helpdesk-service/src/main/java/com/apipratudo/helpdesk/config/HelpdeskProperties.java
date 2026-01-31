package com.apipratudo.helpdesk.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.helpdesk")
public class HelpdeskProperties {

  private int slaMinutes = 30;
  private int summaryMaxChars = 500;
  private Map<String, String> intentKeywords = new LinkedHashMap<>();

  public int getSlaMinutes() {
    return slaMinutes;
  }

  public void setSlaMinutes(int slaMinutes) {
    this.slaMinutes = slaMinutes;
  }

  public int getSummaryMaxChars() {
    return summaryMaxChars;
  }

  public void setSummaryMaxChars(int summaryMaxChars) {
    this.summaryMaxChars = summaryMaxChars;
  }

  public Map<String, String> getIntentKeywords() {
    return intentKeywords;
  }

  public void setIntentKeywords(Map<String, String> intentKeywords) {
    this.intentKeywords = intentKeywords;
  }
}
