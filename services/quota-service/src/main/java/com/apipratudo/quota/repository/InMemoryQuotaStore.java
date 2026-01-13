package com.apipratudo.quota.repository;

import com.apipratudo.quota.config.QuotaProperties;
import com.apipratudo.quota.dto.ApiKeyLimits;
import com.apipratudo.quota.dto.QuotaReason;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.QuotaDecision;
import com.apipratudo.quota.model.QuotaRefundDecision;
import com.apipratudo.quota.model.QuotaStatus;
import com.apipratudo.quota.model.QuotaWindow;
import com.apipratudo.quota.model.QuotaWindowStatus;
import com.apipratudo.quota.model.QuotaWindowType;
import com.apipratudo.quota.model.QuotaWindows;
import com.google.cloud.firestore.Firestore;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(Firestore.class)
public class InMemoryQuotaStore implements QuotaStore {

  private final Clock clock;
  private final QuotaProperties properties;
  private final ConcurrentMap<String, WindowState> windows = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, LedgerEntry> ledger = new ConcurrentHashMap<>();

  public InMemoryQuotaStore(Clock clock, QuotaProperties properties) {
    this.clock = clock;
    this.properties = properties;
  }

  @Override
  public synchronized QuotaDecision consume(
      ApiKey apiKey,
      String requestId,
      String route,
      int cost,
      QuotaWindows windowsSnapshot
  ) {
    Instant now = Instant.now(clock);
    String idempotencyKey = idempotencyKey(apiKey.id(), requestId);
    LedgerEntry existing = ledger.get(idempotencyKey);
    if (existing != null) {
      if (existing.expiresAt().isAfter(now)) {
        return existing.decision();
      }
      ledger.remove(idempotencyKey);
    }

    ApiKeyLimits limits = apiKey.limits();
    QuotaWindow minuteWindow = windowsSnapshot.minute();
    QuotaWindow dayWindow = windowsSnapshot.day();
    WindowDecision minute = evaluateWindow(apiKey.id(), minuteWindow, limits.requestsPerMinute(), cost);
    WindowDecision day = evaluateWindow(apiKey.id(), dayWindow, limits.requestsPerDay(), cost);

    QuotaDecision decision;
    boolean consumed;
    if (minute.exceeded() || day.exceeded()) {
      WindowDecision exceeded = chooseExceeded(minute, day);
      decision = new QuotaDecision(false, QuotaReason.RATE_LIMITED, exceeded.limit(), 0, exceeded.resetAt());
      consumed = false;
    } else {
      windows.put(minute.windowKey(), new WindowState(minute.newCount(), minute.windowStart()));
      windows.put(day.windowKey(), new WindowState(day.newCount(), day.windowStart()));
      WindowDecision selected = chooseMostRestrictive(minute, day);
      decision = new QuotaDecision(true, null, selected.limit(), selected.remaining(), selected.resetAt());
      consumed = true;
    }

    Instant expiresAt = now.plusSeconds(properties.getIdempotencyTtlSeconds());
    ledger.put(idempotencyKey, new LedgerEntry(decision, expiresAt, minute.windowStart(), day.windowStart(), cost,
        consumed, false));
    return decision;
  }

  @Override
  public synchronized QuotaRefundDecision refund(ApiKey apiKey, String requestId) {
    Instant now = Instant.now(clock);
    String idempotencyKey = idempotencyKey(apiKey.id(), requestId);
    LedgerEntry entry = ledger.get(idempotencyKey);
    if (entry == null || entry.expiresAt().isBefore(now)) {
      return new QuotaRefundDecision(false, null, null, null, null);
    }
    if (entry.refunded()) {
      return new QuotaRefundDecision(true, entry.decision().reason(), entry.decision().limit(),
          entry.decision().remaining(), entry.decision().resetAt());
    }
    if (!entry.consumed()) {
      return new QuotaRefundDecision(false, entry.decision().reason(), entry.decision().limit(),
          entry.decision().remaining(), entry.decision().resetAt());
    }

    decrementWindow(apiKey.id(), QuotaWindowType.MINUTE, entry.minuteWindowStart(), entry.cost());
    decrementWindow(apiKey.id(), QuotaWindowType.DAY, entry.dayWindowStart(), entry.cost());
    LedgerEntry refunded = entry.withRefunded();
    ledger.put(idempotencyKey, refunded);
    return new QuotaRefundDecision(true, null, null, null, null);
  }

  @Override
  public synchronized QuotaStatus status(ApiKey apiKey, QuotaWindows windowsSnapshot) {
    ApiKeyLimits limits = apiKey.limits();

    QuotaWindowStatus minute = readWindow(apiKey.id(), windowsSnapshot.minute(), limits.requestsPerMinute());
    QuotaWindowStatus day = readWindow(apiKey.id(), windowsSnapshot.day(), limits.requestsPerDay());
    return new QuotaStatus(minute, day);
  }

  private void decrementWindow(String apiKeyId, QuotaWindowType type, Instant windowStart, int cost) {
    String key = windowKey(apiKeyId, type, windowStart);
    WindowState state = windows.get(key);
    if (state == null) {
      return;
    }
    long newCount = Math.max(state.count() - cost, 0);
    windows.put(key, new WindowState(newCount, state.windowStart()));
  }

  private QuotaWindowStatus readWindow(String apiKeyId, QuotaWindow window, long limit) {
    String key = windowKey(apiKeyId, window);
    WindowState state = windows.get(key);
    long used = state == null ? 0 : state.count();
    long remaining = Math.max(limit - used, 0);
    return new QuotaWindowStatus(limit, used, remaining, window.resetAt());
  }

  private WindowDecision evaluateWindow(
      String apiKeyId,
      QuotaWindow window,
      long limit,
      int cost
  ) {
    String key = windowKey(apiKeyId, window);
    WindowState state = windows.get(key);
    long current = state == null ? 0 : state.count();
    long newCount = current + cost;
    long remaining = Math.max(limit - newCount, 0);
    long overage = Math.max(newCount - limit, 0);
    boolean exceeded = newCount > limit;
    return new WindowDecision(key, window.windowStart(), limit, current, newCount, remaining, overage,
        window.resetAt(), exceeded);
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

  private String windowKey(String apiKeyId, QuotaWindow window) {
    return windowKey(apiKeyId, window.type(), window.windowStart());
  }

  private String windowKey(String apiKeyId, QuotaWindowType type, Instant windowStart) {
    return apiKeyId + ":" + type.name() + ":" + windowStart.toEpochMilli();
  }

  private String idempotencyKey(String apiKeyId, String requestId) {
    return apiKeyId + ":" + requestId;
  }

  private record WindowState(long count, Instant windowStart) {
  }

  private record LedgerEntry(
      QuotaDecision decision,
      Instant expiresAt,
      Instant minuteWindowStart,
      Instant dayWindowStart,
      int cost,
      boolean consumed,
      boolean refunded
  ) {

    LedgerEntry withRefunded() {
      return new LedgerEntry(decision, expiresAt, minuteWindowStart, dayWindowStart, cost, consumed, true);
    }
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
