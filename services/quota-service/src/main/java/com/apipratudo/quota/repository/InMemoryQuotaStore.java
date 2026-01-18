package com.apipratudo.quota.repository;

import com.apipratudo.quota.config.QuotaProperties;
import com.apipratudo.quota.dto.ApiKeyLimits;
import com.apipratudo.quota.dto.QuotaReason;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.ApiKeyCredits;
import com.apipratudo.quota.model.QuotaDecision;
import com.apipratudo.quota.model.QuotaRefundDecision;
import com.apipratudo.quota.model.QuotaStatus;
import com.apipratudo.quota.model.QuotaWindow;
import com.apipratudo.quota.model.QuotaWindowStatus;
import com.apipratudo.quota.model.QuotaWindows;
import com.google.cloud.firestore.Firestore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(Firestore.class)
public class InMemoryQuotaStore implements QuotaStore {

  private final Clock clock;
  private final QuotaProperties properties;
  private final ApiKeyRepository apiKeyRepository;
  private final ConcurrentMap<String, LedgerEntry> ledger = new ConcurrentHashMap<>();

  public InMemoryQuotaStore(Clock clock, QuotaProperties properties, ApiKeyRepository apiKeyRepository) {
    this.clock = clock;
    this.properties = properties;
    this.apiKeyRepository = apiKeyRepository;
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

    UsageState usage = currentUsage(apiKey, minuteWindow, dayWindow);

    WindowDecision minute = evaluateWindow(minuteWindow, limits.requestsPerMinute(), usage.minuteCount(), cost);
    WindowDecision day = evaluateWindow(dayWindow, limits.requestsPerDay(), usage.dayCount(), cost);

    QuotaDecision decision;
    boolean consumed;
    long creditsRemaining = apiKey.credits() == null ? 0 : apiKey.credits().remaining();
    long creditsConsumed = 0;
    if (creditsRemaining >= cost) {
      if (minute.exceeded()) {
        decision = new QuotaDecision(false, QuotaReason.QUOTA_EXCEEDED, minute.limit(), 0, minute.resetAt());
        consumed = false;
      } else {
        long newCredits = Math.max(creditsRemaining - cost, 0);
        creditsConsumed = cost;
        decision = new QuotaDecision(true, null, minute.limit(), minute.remaining(), minute.resetAt());
        consumed = true;
        Instant minuteBucket = usage.minuteBucket();
        String dayBucket = usage.dayBucket();
        long minuteCount = minute.newCount();
        long dayCount = apiKey.dayCount();

        if (newCredits == 0) {
          minuteCount = 0;
          dayCount = 0;
        }

        ApiKey updated = new ApiKey(
            apiKey.id(),
            apiKey.apiKeyHash(),
            apiKey.name(),
            apiKey.owner(),
            apiKey.ownerEmail(),
            apiKey.orgName(),
            apiKey.limits(),
            apiKey.createdAt(),
            apiKey.status(),
            apiKey.plan(),
            new ApiKeyCredits(newCredits),
            minuteBucket,
            minuteCount,
            dayBucket,
            dayCount
        );
        apiKeyRepository.save(updated);
      }
    } else {
      if (minute.exceeded() || day.exceeded()) {
        WindowDecision exceeded = chooseExceeded(minute, day);
        decision = new QuotaDecision(false, QuotaReason.QUOTA_EXCEEDED, exceeded.limit(), 0, exceeded.resetAt());
        consumed = false;
      } else {
        WindowDecision selected = chooseMostRestrictive(minute, day);
        decision = new QuotaDecision(true, null, selected.limit(), selected.remaining(), selected.resetAt());
        consumed = true;
        ApiKey updated = new ApiKey(
            apiKey.id(),
            apiKey.apiKeyHash(),
            apiKey.name(),
            apiKey.owner(),
            apiKey.ownerEmail(),
            apiKey.orgName(),
            apiKey.limits(),
            apiKey.createdAt(),
            apiKey.status(),
            apiKey.plan(),
            apiKey.credits(),
            usage.minuteBucket(),
            minute.newCount(),
            usage.dayBucket(),
            day.newCount()
        );
        apiKeyRepository.save(updated);
      }
    }

    Instant expiresAt = now.plusSeconds(properties.getIdempotencyTtlSeconds());
    ledger.put(idempotencyKey, new LedgerEntry(decision, expiresAt, usage.minuteBucket(),
        creditsRemaining > 0 ? null : usage.dayBucket(), cost, consumed, false, creditsConsumed));
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

    ApiKey updated = updateUsageForRefund(apiKey, entry);
    if (updated != null) {
      apiKeyRepository.save(updated);
    }

    LedgerEntry refunded = entry.withRefunded();
    ledger.put(idempotencyKey, refunded);
    return new QuotaRefundDecision(true, null, null, null, null);
  }

  @Override
  public synchronized QuotaStatus status(ApiKey apiKey, QuotaWindows windowsSnapshot) {
    ApiKeyLimits limits = apiKey.limits();

    QuotaWindow minuteWindow = windowsSnapshot.minute();
    QuotaWindow dayWindow = windowsSnapshot.day();

    UsageState usage = currentUsage(apiKey, minuteWindow, dayWindow);

    QuotaWindowStatus minute = new QuotaWindowStatus(
        limits.requestsPerMinute(),
        usage.minuteCount(),
        Math.max(limits.requestsPerMinute() - usage.minuteCount(), 0),
        minuteWindow.resetAt()
    );
    QuotaWindowStatus day = new QuotaWindowStatus(
        limits.requestsPerDay(),
        usage.dayCount(),
        Math.max(limits.requestsPerDay() - usage.dayCount(), 0),
        dayWindow.resetAt()
    );
    return new QuotaStatus(minute, day);
  }

  private UsageState currentUsage(ApiKey apiKey, QuotaWindow minuteWindow, QuotaWindow dayWindow) {
    Instant minuteBucket = minuteWindow.windowStart();
    String dayBucket = dayBucket(dayWindow.windowStart());
    long minuteCount = matchesBucket(apiKey.minuteBucket(), minuteBucket) ? apiKey.minuteCount() : 0;
    long dayCount = dayBucket.equals(apiKey.dayBucket()) ? apiKey.dayCount() : 0;
    return new UsageState(minuteBucket, minuteCount, dayBucket, dayCount);
  }

  private ApiKey updateUsageForRefund(ApiKey apiKey, LedgerEntry entry) {
    long minuteCount = apiKey.minuteCount();
    long dayCount = apiKey.dayCount();
    long creditsRemaining = apiKey.credits() == null ? 0 : apiKey.credits().remaining();
    boolean updated = false;

    if (matchesBucket(apiKey.minuteBucket(), entry.minuteBucket())) {
      minuteCount = Math.max(minuteCount - entry.cost(), 0);
      updated = true;
    }
    if (entry.dayBucket() != null && entry.dayBucket().equals(apiKey.dayBucket())) {
      dayCount = Math.max(dayCount - entry.cost(), 0);
      updated = true;
    }
    if (entry.creditsConsumed() > 0) {
      creditsRemaining += entry.creditsConsumed();
      updated = true;
    }

    if (!updated) {
      return null;
    }

    return new ApiKey(
        apiKey.id(),
        apiKey.apiKeyHash(),
        apiKey.name(),
        apiKey.owner(),
        apiKey.ownerEmail(),
        apiKey.orgName(),
        apiKey.limits(),
        apiKey.createdAt(),
        apiKey.status(),
        apiKey.plan(),
        new ApiKeyCredits(creditsRemaining),
        apiKey.minuteBucket(),
        minuteCount,
        apiKey.dayBucket(),
        dayCount
    );
  }

  private WindowDecision evaluateWindow(QuotaWindow window, long limit, long current, int cost) {
    long newCount = current + cost;
    long remaining = Math.max(limit - newCount, 0);
    long overage = Math.max(newCount - limit, 0);
    boolean exceeded = newCount > limit;
    return new WindowDecision(limit, newCount, remaining, overage, window.resetAt(), exceeded);
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

  private boolean matchesBucket(Instant current, Instant target) {
    if (current == null || target == null) {
      return false;
    }
    return current.equals(target);
  }

  private String dayBucket(Instant dayWindowStart) {
    return LocalDate.ofInstant(dayWindowStart, ZoneOffset.UTC).toString();
  }

  private String idempotencyKey(String apiKeyId, String requestId) {
    return apiKeyId + ":" + requestId;
  }

  private record LedgerEntry(
      QuotaDecision decision,
      Instant expiresAt,
      Instant minuteBucket,
      String dayBucket,
      int cost,
      boolean consumed,
      boolean refunded,
      long creditsConsumed
  ) {

    LedgerEntry withRefunded() {
      return new LedgerEntry(decision, expiresAt, minuteBucket, dayBucket, cost, consumed, true, creditsConsumed);
    }
  }

  private record WindowDecision(
      long limit,
      long newCount,
      long remaining,
      long overage,
      Instant resetAt,
      boolean exceeded
  ) {
  }

  private record UsageState(
      Instant minuteBucket,
      long minuteCount,
      String dayBucket,
      long dayCount
  ) {
  }
}
