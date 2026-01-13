package com.apipratudo.quota.service;

import com.apipratudo.quota.model.QuotaWindow;
import com.apipratudo.quota.model.QuotaWindowType;
import com.apipratudo.quota.model.QuotaWindows;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class QuotaWindowCalculator {

  private final Clock clock;

  public QuotaWindowCalculator(Clock clock) {
    this.clock = clock;
  }

  public QuotaWindows currentWindows() {
    Instant now = Instant.now(clock);
    return new QuotaWindows(
        windowFor(now, QuotaWindowType.MINUTE),
        windowFor(now, QuotaWindowType.DAY)
    );
  }

  private QuotaWindow windowFor(Instant now, QuotaWindowType type) {
    return switch (type) {
      case MINUTE -> {
        Instant windowStart = now.truncatedTo(ChronoUnit.MINUTES);
        Instant resetAt = windowStart.plus(1, ChronoUnit.MINUTES);
        yield new QuotaWindow(type, windowStart, resetAt);
      }
      case DAY -> {
        Instant windowStart = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant resetAt = windowStart.plus(1, ChronoUnit.DAYS);
        yield new QuotaWindow(type, windowStart, resetAt);
      }
    };
  }
}
