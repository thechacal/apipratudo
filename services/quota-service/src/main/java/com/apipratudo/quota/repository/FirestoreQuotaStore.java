package com.apipratudo.quota.repository;

import com.apipratudo.quota.config.FirestoreProperties;
import com.apipratudo.quota.config.QuotaProperties;
import com.apipratudo.quota.dto.ApiKeyLimits;
import com.apipratudo.quota.dto.QuotaReason;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.QuotaDecision;
import com.apipratudo.quota.model.QuotaRefundDecision;
import com.apipratudo.quota.model.QuotaStatus;
import com.apipratudo.quota.model.QuotaWindow;
import com.apipratudo.quota.model.QuotaWindowStatus;
import com.apipratudo.quota.model.QuotaWindows;
import com.apipratudo.quota.service.HashingUtils;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(Firestore.class)
public class FirestoreQuotaStore implements QuotaStore {

  private final Firestore firestore;
  private final FirestoreProperties properties;
  private final QuotaProperties quotaProperties;
  private final Clock clock;

  public FirestoreQuotaStore(
      Firestore firestore,
      FirestoreProperties properties,
      QuotaProperties quotaProperties,
      Clock clock
  ) {
    this.firestore = firestore;
    this.properties = properties;
    this.quotaProperties = quotaProperties;
    this.clock = clock;
  }

  @Override
  public QuotaDecision consume(
      ApiKey apiKey,
      String requestId,
      String route,
      int cost,
      QuotaWindows windowsSnapshot
  ) {
    String idempotencyId = idempotencyId(apiKey.id(), requestId);
    Instant now = Instant.now(clock);
    QuotaWindow minuteWindow = windowsSnapshot.minute();
    QuotaWindow dayWindow = windowsSnapshot.day();
    DocumentReference idempotencyRef = idempotencyRef(idempotencyId);
    DocumentReference apiKeyRef = apiKeyRef(apiKey.id());

    ApiKeyLimits limits = apiKey.limits();
    long minuteLimit = limits.requestsPerMinute();
    long dayLimit = limits.requestsPerDay();

    ApiFuture<QuotaDecision> future = firestore.runTransaction(transaction -> {
      DocumentSnapshot idempotencySnapshot = transaction.get(idempotencyRef).get();
      if (idempotencySnapshot.exists() && !isExpired(idempotencySnapshot, now)) {
        return decisionFromSnapshot(idempotencySnapshot);
      }

      DocumentSnapshot apiKeySnapshot = transaction.get(apiKeyRef).get();
      Instant storedMinuteBucket = toInstant(apiKeySnapshot.getTimestamp("minuteBucket"));
      long storedMinuteCount = getLong(apiKeySnapshot, "minuteCount");
      String storedDayBucket = apiKeySnapshot.getString("dayBucket");
      long storedDayCount = getLong(apiKeySnapshot, "dayCount");

      Instant minuteBucket = minuteWindow.windowStart();
      String dayBucket = dayBucket(dayWindow.windowStart());

      long minuteCount = matchesBucket(storedMinuteBucket, minuteBucket) ? storedMinuteCount : 0;
      long dayCount = dayBucket.equals(storedDayBucket) ? storedDayCount : 0;

      WindowDecision minute = buildWindowDecision(minuteWindow, minuteLimit, minuteCount, cost);
      WindowDecision day = buildWindowDecision(dayWindow, dayLimit, dayCount, cost);

      QuotaDecision decision;
      boolean consumed;
      if (minute.exceeded() || day.exceeded()) {
        WindowDecision exceeded = chooseExceeded(minute, day);
        decision = new QuotaDecision(false, QuotaReason.QUOTA_EXCEEDED, exceeded.limit(), 0, exceeded.resetAt());
        consumed = false;
      } else {
        WindowDecision selected = chooseMostRestrictive(minute, day);
        decision = new QuotaDecision(true, null, selected.limit(), selected.remaining(), selected.resetAt());
        consumed = true;

        Map<String, Object> apiKeyUpdates = new HashMap<>();
        apiKeyUpdates.put("minuteBucket", toTimestamp(minuteBucket));
        apiKeyUpdates.put("minuteCount", minute.newCount());
        apiKeyUpdates.put("dayBucket", dayBucket);
        apiKeyUpdates.put("dayCount", day.newCount());
        apiKeyUpdates.put("updatedAt", toTimestamp(now));
        transaction.set(apiKeyRef, apiKeyUpdates, SetOptions.merge());
      }

      Map<String, Object> idempotencyData = idempotencyData(apiKey, requestId, route, decision, now, cost,
          minuteBucket, dayBucket, consumed);
      transaction.set(idempotencyRef, idempotencyData, SetOptions.merge());
      return decision;
    });

    return getFuture(future, "Quota consume interrupted", "Failed to consume quota");
  }

  @Override
  public QuotaRefundDecision refund(ApiKey apiKey, String requestId) {
    String idempotencyId = idempotencyId(apiKey.id(), requestId);
    Instant now = Instant.now(clock);
    DocumentReference idempotencyRef = idempotencyRef(idempotencyId);
    DocumentReference apiKeyRef = apiKeyRef(apiKey.id());

    ApiFuture<QuotaRefundDecision> future = firestore.runTransaction(transaction -> {
      DocumentSnapshot snapshot = transaction.get(idempotencyRef).get();
      if (!snapshot.exists() || isExpired(snapshot, now)) {
        return new QuotaRefundDecision(false, null, null, null, null);
      }

      QuotaDecision decision = decisionFromSnapshot(snapshot);
      boolean refunded = Boolean.TRUE.equals(snapshot.getBoolean("refunded"));
      boolean consumed = Boolean.TRUE.equals(snapshot.getBoolean("consumed"));
      if (refunded) {
        return new QuotaRefundDecision(true, decision.reason(), decision.limit(), decision.remaining(),
            decision.resetAt());
      }
      if (!consumed) {
        return new QuotaRefundDecision(false, decision.reason(), decision.limit(), decision.remaining(),
            decision.resetAt());
      }

      int cost = (int) getLong(snapshot, "cost");
      Instant entryMinuteBucket = toInstant(snapshot.getTimestamp("minuteBucket"));
      String entryDayBucket = snapshot.getString("dayBucket");

      DocumentSnapshot apiKeySnapshot = transaction.get(apiKeyRef).get();
      Instant storedMinuteBucket = toInstant(apiKeySnapshot.getTimestamp("minuteBucket"));
      long storedMinuteCount = getLong(apiKeySnapshot, "minuteCount");
      String storedDayBucket = apiKeySnapshot.getString("dayBucket");
      long storedDayCount = getLong(apiKeySnapshot, "dayCount");

      boolean updated = false;
      long newMinuteCount = storedMinuteCount;
      long newDayCount = storedDayCount;

      if (entryMinuteBucket != null && matchesBucket(storedMinuteBucket, entryMinuteBucket)) {
        newMinuteCount = Math.max(storedMinuteCount - cost, 0);
        updated = true;
      }
      if (entryDayBucket != null && entryDayBucket.equals(storedDayBucket)) {
        newDayCount = Math.max(storedDayCount - cost, 0);
        updated = true;
      }

      if (updated) {
        Map<String, Object> apiKeyUpdates = new HashMap<>();
        apiKeyUpdates.put("minuteCount", newMinuteCount);
        apiKeyUpdates.put("dayCount", newDayCount);
        apiKeyUpdates.put("updatedAt", toTimestamp(now));
        transaction.set(apiKeyRef, apiKeyUpdates, SetOptions.merge());
      }

      Map<String, Object> updates = new HashMap<>();
      updates.put("refunded", true);
      updates.put("refundedAt", toTimestamp(now));
      transaction.set(idempotencyRef, updates, SetOptions.merge());

      return new QuotaRefundDecision(true, decision.reason(), decision.limit(), decision.remaining(), decision.resetAt());
    });

    return getFuture(future, "Quota refund interrupted", "Failed to refund quota");
  }

  @Override
  public QuotaStatus status(ApiKey apiKey, QuotaWindows windowsSnapshot) {
    QuotaWindow minuteWindow = windowsSnapshot.minute();
    QuotaWindow dayWindow = windowsSnapshot.day();
    UsageState usage = currentUsage(apiKey, minuteWindow, dayWindow);

    ApiKeyLimits limits = apiKey.limits();
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

  private String idempotencyId(String apiKeyId, String requestId) {
    return HashingUtils.sha256Hex(apiKeyId + ":" + requestId);
  }

  private DocumentReference idempotencyRef(String id) {
    return firestore.collection(properties.getCollections().getIdempotencyQuota()).document(id);
  }

  private DocumentReference apiKeyRef(String apiKeyId) {
    return firestore.collection(properties.getCollections().getApiKeys()).document(apiKeyId);
  }

  private WindowDecision buildWindowDecision(QuotaWindow window, long limit, long current, int cost) {
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

  private Map<String, Object> idempotencyData(
      ApiKey apiKey,
      String requestId,
      String route,
      QuotaDecision decision,
      Instant now,
      int cost,
      Instant minuteBucket,
      String dayBucket,
      boolean consumed
  ) {
    Map<String, Object> data = new HashMap<>();
    data.put("apiKeyId", apiKey.id());
    data.put("requestId", requestId);
    data.put("route", route);
    data.put("allowed", decision.allowed());
    data.put("consumed", consumed);
    data.put("refunded", false);
    data.put("cost", cost);
    data.put("minuteBucket", toTimestamp(minuteBucket));
    data.put("dayBucket", dayBucket);
    if (decision.reason() != null) {
      data.put("reason", decision.reason().name());
    }
    data.put("limit", decision.limit());
    data.put("remaining", decision.remaining());
    data.put("resetAt", toTimestamp(decision.resetAt()));
    data.put("createdAt", toTimestamp(now));
    Instant expiresAt = now.plusSeconds(quotaProperties.getIdempotencyTtlSeconds());
    data.put("expiresAt", toTimestamp(expiresAt));
    return data;
  }

  private boolean isExpired(DocumentSnapshot snapshot, Instant now) {
    Timestamp expiresAt = snapshot.getTimestamp("expiresAt");
    if (expiresAt == null) {
      return false;
    }
    Instant expiry = Instant.ofEpochSecond(expiresAt.getSeconds(), expiresAt.getNanos());
    return expiry.isBefore(now);
  }

  private QuotaDecision decisionFromSnapshot(DocumentSnapshot snapshot) {
    boolean allowed = Boolean.TRUE.equals(snapshot.getBoolean("allowed"));
    String reasonRaw = snapshot.getString("reason");
    QuotaReason reason = reasonRaw == null ? null : QuotaReason.valueOf(reasonRaw);
    long limit = getLong(snapshot, "limit");
    long remaining = getLong(snapshot, "remaining");
    Timestamp resetAt = snapshot.getTimestamp("resetAt");
    Instant resetAtInstant = toInstant(resetAt);
    return new QuotaDecision(allowed, reason, limit, remaining, resetAtInstant);
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

  private Instant toInstant(Timestamp timestamp) {
    if (timestamp == null) {
      return null;
    }
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  private long getLong(DocumentSnapshot snapshot, String field) {
    Long value = snapshot.getLong(field);
    return value == null ? 0 : value;
  }

  private Timestamp toTimestamp(Instant instant) {
    if (instant == null) {
      return null;
    }
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
  }

  private <T> T getFuture(ApiFuture<T> future, String interruptedMessage, String failedMessage) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interruptedMessage, e);
    } catch (ExecutionException e) {
      throw new IllegalStateException(failedMessage, e);
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
