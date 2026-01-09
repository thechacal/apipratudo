package com.apipratudo.gateway.webhook.service;

import com.apipratudo.gateway.config.DeliveryProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DeliveryRetryPolicy {

  private static final DateTimeFormatter RETRY_AFTER_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;

  private final DeliveryProperties properties;
  private final Clock clock;
  private final Random random;

  @Autowired
  public DeliveryRetryPolicy(DeliveryProperties properties, Clock clock) {
    this(properties, clock, new Random());
  }

  DeliveryRetryPolicy(DeliveryProperties properties, Clock clock, Random random) {
    this.properties = properties;
    this.clock = clock;
    this.random = random;
  }

  public RetryPlan nextRetry(int attempt, Integer statusCode, String retryAfterHeader, boolean exception) {
    if (attempt >= properties.getMaxAttempts()) {
      return RetryPlan.noRetry();
    }

    boolean shouldRetry = exception || statusCode == null;
    if (!shouldRetry && statusCode >= 500 && properties.isRetryOn5xx()) {
      shouldRetry = true;
    }
    if (!shouldRetry && statusCode == 429 && properties.isRetryOn429()) {
      shouldRetry = true;
    }

    if (!shouldRetry) {
      return RetryPlan.noRetry();
    }

    long backoffMs = computeBackoffMs(attempt);
    Long retryAfterMs = statusCode != null && statusCode == 429 ? parseRetryAfterMs(retryAfterHeader) : null;
    long delayMs = retryAfterMs == null ? backoffMs : Math.max(backoffMs, retryAfterMs);
    return new RetryPlan(true, Duration.ofMillis(delayMs));
  }

  private long computeBackoffMs(int attempt) {
    long initial = properties.getInitialBackoffMs();
    long max = properties.getMaxBackoffMs();
    if (initial <= 0) {
      return 0;
    }
    long backoff = initial;
    for (int i = 1; i < attempt; i++) {
      if (backoff >= max / 2) {
        backoff = max;
        break;
      }
      backoff = backoff * 2;
    }
    if (backoff > max) {
      backoff = max;
    }
    long jitter = (long) (backoff * 0.2 * random.nextDouble());
    long withJitter = backoff + jitter;
    return withJitter > max ? max : withJitter;
  }

  private Long parseRetryAfterMs(String retryAfterHeader) {
    if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
      return null;
    }
    String trimmed = retryAfterHeader.trim();
    try {
      long seconds = Long.parseLong(trimmed);
      return Math.max(0, seconds) * 1000L;
    } catch (NumberFormatException ignored) {
      // fall through
    }

    try {
      ZonedDateTime retryAt = ZonedDateTime.parse(trimmed, RETRY_AFTER_FORMATTER);
      long delay = Duration.between(Instant.now(clock), retryAt.toInstant()).toMillis();
      return Math.max(delay, 0);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  public record RetryPlan(boolean retry, Duration delay) {
    static RetryPlan noRetry() {
      return new RetryPlan(false, Duration.ZERO);
    }
  }
}
