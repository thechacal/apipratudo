package com.apipratudo.diadesorte.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "app.playwright")
@Validated
public class PlaywrightConfig {

  @Min(1000)
  private int timeoutMs = 12000;

  @Min(1000)
  private int navigationTimeoutMs = 12000;

  @NotBlank
  private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
      + "AppleWebKit/537.36 (KHTML, like Gecko) "
      + "Chrome/113.0.0.0 Safari/537.36";

  public int getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(int timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public int getNavigationTimeoutMs() {
    return navigationTimeoutMs;
  }

  public void setNavigationTimeoutMs(int navigationTimeoutMs) {
    this.navigationTimeoutMs = navigationTimeoutMs;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
