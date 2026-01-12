package com.apipratudo.quota.repository;

import com.apipratudo.quota.config.QuotaProperties;
import com.apipratudo.quota.dto.ApiKeyLimits;
import com.apipratudo.quota.dto.QuotaReason;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.QuotaDecision;
import com.apipratudo.quota.model.QuotaWindowType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryQuotaStore implements QuotaStore {

  private final Clock clock;
  private final QuotaProperties properties;
  private final ConcurrentMap<String, WindowState> windows = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, IdempotencyEntry> idempotency = new ConcurrentHashMap<>();

  public InMemoryQuotaStore(Clock clock, QuotaProperties properties) {
    this.clock = clock;
    this.properties = properties;
  }

  @Override
  public synchronized QuotaDecision consume(ApiKey apiKey, String requestId, String route, int cost) {
    Instant now = Instant.now(clock);
    String idempotencyKey = idempotencyKey(apiKey.id(), requestId);
    IdempotencyEntry existing = idempotency.get(idempotencyKey);
    if (existing != null) {
      if (existing.expiresAt().isAfter(now)) {
        return existing.decision();
      }
      idempotency.remove(idempotencyKey);
    }

    ApiKeyLimits limits = apiKey.limits();
    WindowDecision minute = evaluateWindow(apiKey.id(), QuotaWindowType.MINUTE, limits.requestsPerMinute(), cost, now);
    WindowDecision day = evaluateWindow(apiKey.id(), QuotaWindowType.DAY, limits.requestsPerDay(), cost, now);

    QuotaDecision decision;
    if (minute.exceeded() || day.exceeded()) {
      WindowDecision exceeded = chooseExceeded(minute, day);
      decision = new QuotaDecision(false, QuotaReason.RATE_LIMITED, exceeded.limit(), 0, exceeded.resetAt());
    } else {
      windows.put(minute.windowKey(), new WindowState(minute.newCount(), minute.windowStart()));
      windows.put(day.windowKey(), new WindowState(day.newCount(), day.windowStart()));
      WindowDecision selected = chooseMostRestrictive(minute, day);
      decision = new QuotaDecision(true, null, selected.limit(), selected.remaining(), selected.resetAt());
    }

    Instant expiresAt = now.plusSeconds(properties.getIdempotencyTtlSeconds());
    idempotency.put(idempotencyKey, new IdempotencyEntry(decision, expiresAt));
    return decision;
  }

  private WindowDecision evaluateWindow(
      String apiKeyId,
      QuotaWindowType type,
      long limit,
      int cost,
      Instant now
  ) {
    Instant windowStart = windowStart(type, now);
    String key = windowKey(apiKeyId, type, windowStart);
    WindowState state = windows.get(key);
    long current = state == null ? 0 : state.count();
    long newCount = current + cost;
    long remaining = Math.max(limit - newCount, 0);
    long overage = Math.max(newCount - limit, 0);
    boolean exceeded = newCount > limit;
    Instant resetAt = windowStart.plus(type.duration());
    return new WindowDecision(key, windowStart, limit, current, newCount, remaining, overage, resetAt, exceeded);
  }

  private WindowDecision chooseMostRestrictive(WindowDecision first, WindowDecision second) {
    if (first.remaining() < second.remaining()) {
      return first;
    }
    if (first.remaining() > second.remaining()) {
      return second;
    }
    return first.resetAt().isBefore(second.resetAt()) ? first : second;
  }

  private WindowDecision chooseExceeded(WindowDecision first, WindowDecision second) {
    if (first.exceeded() && !second.exceeded()) {
      return first;
    }
    if (second.exceeded() && !first.exceeded()) {
      return second;
    }
    if (first.overage() > second.overage()) {
      return first;
    }
    if (second.overage() > first.overage()) {
      return second;
    }
    return first.resetAt().isBefore(second.resetAt()) ? first : second;
  }

  private Instant windowStart(QuotaWindowType type, Instant now) {
    return switch (type) {
      case MINUTE -> now.truncatedTo(ChronoUnit.MINUTES);
      case DAY -> now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    };
  }

  private String windowKey(String apiKeyId, QuotaWindowType type, Instant windowStart) {
    return apiKeyId + ":" + type.name() + ":" + windowStart.toEpochMilli();
  }

  private String idempotencyKey(String apiKeyId, String requestId) {
    return apiKeyId + ":" + requestId;
  }

  private record WindowState(long count, Instant windowStart) {
  }

  private record IdempotencyEntry(QuotaDecision decision, Instant expiresAt) {
  }

  private record WindowDecision(
      String windowKey,
      Instant windowStart,
      long limit,
      long current,
      long newCount,
      long remaining,
      long overage,
      Instant resetAt,
      boolean exceeded
  ) {
  }
}
