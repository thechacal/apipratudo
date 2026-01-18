package com.apipratudo.portal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
public class RateLimitProperties {

  @Valid
  private Ip ip = new Ip();

  @Valid
  private Email email = new Email();

  @Valid
  private Org org = new Org();

  public Ip getIp() {
    return ip;
  }

  public void setIp(Ip ip) {
    this.ip = ip;
  }

  public Email getEmail() {
    return email;
  }

  public void setEmail(Email email) {
    this.email = email;
  }

  public Org getOrg() {
    return org;
  }

  public void setOrg(Org org) {
    this.org = org;
  }

  public static class Ip {
    @Min(1)
    private int requestsPerMinute = 10;

    public int getRequestsPerMinute() {
      return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
      this.requestsPerMinute = requestsPerMinute;
    }
  }

  public static class Email {
    @Min(1)
    private int maxPerDay = 1;

    public int getMaxPerDay() {
      return maxPerDay;
    }

    public void setMaxPerDay(int maxPerDay) {
      this.maxPerDay = maxPerDay;
    }
  }

  public static class Org {
    @Min(1)
    private int maxPerDay = 3;

    public int getMaxPerDay() {
      return maxPerDay;
    }

    public void setMaxPerDay(int maxPerDay) {
      this.maxPerDay = maxPerDay;
    }
  }
}
