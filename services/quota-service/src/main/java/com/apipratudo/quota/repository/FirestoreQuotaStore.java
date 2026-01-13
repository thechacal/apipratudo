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
import com.apipratudo.quota.model.QuotaWindowType;
import com.apipratudo.quota.model.QuotaWindows;
import com.apipratudo.quota.service.HashingUtils;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.Transaction;
import java.time.Clock;
import java.time.Instant;
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

    DocumentReference minuteRef = windowRef(apiKey.id(), minuteWindow);
    DocumentReference dayRef = windowRef(apiKey.id(), dayWindow);

    ApiKeyLimits limits = apiKey.limits();
    long minuteLimit = limits.requestsPerMinute();
    long dayLimit = limits.requestsPerDay();

    ApiFuture<QuotaDecision> future = firestore.runTransaction(transaction -> {
      DocumentSnapshot idempotencySnapshot = transaction.get(idempotencyRef).get();
      if (idempotencySnapshot.exists() && !isExpired(idempotencySnapshot, now)) {
        return decisionFromSnapshot(idempotencySnapshot);
      }

      DocumentSnapshot minuteSnapshot = transaction.get(minuteRef).get();
      DocumentSnapshot daySnapshot = transaction.get(dayRef).get();

      WindowDecision minute = buildWindowDecision(minuteSnapshot, apiKey.id(), minuteWindow, minuteLimit, cost);
      WindowDecision day = buildWindowDecision(daySnapshot, apiKey.id(), dayWindow, dayLimit, cost);

      QuotaDecision decision;
      boolean consumed;
      if (minute.exceeded() || day.exceeded()) {
        WindowDecision exceeded = chooseExceeded(minute, day);
        decision = new QuotaDecision(false, QuotaReason.RATE_LIMITED, exceeded.limit(), 0, exceeded.resetAt());
        consumed = false;
      } else {
        persistWindow(transaction, minuteRef, minuteSnapshot, minute, now);
        persistWindow(transaction, dayRef, daySnapshot, day, now);
        WindowDecision selected = chooseMostRestrictive(minute, day);
        decision = new QuotaDecision(true, null, selected.limit(), selected.remaining(), selected.resetAt());
        consumed = true;
      }

      Map<String, Object> idempotencyData = idempotencyData(apiKey, requestId, route, decision, now, cost,
          minuteWindow, dayWindow, consumed);
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
      Instant minuteStart = toInstant(snapshot.getTimestamp("minuteWindowStart"));
      Instant dayStart = toInstant(snapshot.getTimestamp("dayWindowStart"));

      if (minuteStart != null) {
        DocumentReference minuteRef = windowRef(apiKey.id(), QuotaWindowType.MINUTE, minuteStart);
        DocumentSnapshot minuteSnapshot = transaction.get(minuteRef).get();
        updateWindowCount(transaction, minuteRef, minuteSnapshot, apiKey.id(), QuotaWindowType.MINUTE,
            minuteStart, cost, now);
      }
      if (dayStart != null) {
        DocumentReference dayRef = windowRef(apiKey.id(), QuotaWindowType.DAY, dayStart);
        DocumentSnapshot daySnapshot = transaction.get(dayRef).get();
        updateWindowCount(transaction, dayRef, daySnapshot, apiKey.id(), QuotaWindowType.DAY, dayStart, cost, now);
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

    DocumentReference minuteRef = windowRef(apiKey.id(), minuteWindow);
    DocumentReference dayRef = windowRef(apiKey.id(), dayWindow);

    try {
      DocumentSnapshot minuteSnapshot = minuteRef.get().get();
      DocumentSnapshot daySnapshot = dayRef.get().get();

      ApiKeyLimits limits = apiKey.limits();
      QuotaWindowStatus minute = toWindowStatus(minuteSnapshot, limits.requestsPerMinute(), minuteWindow);
      QuotaWindowStatus day = toWindowStatus(daySnapshot, limits.requestsPerDay(), dayWindow);
      return new QuotaStatus(minute, day);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Quota status interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch quota status", e);
    }
  }

  private QuotaWindowStatus toWindowStatus(
      DocumentSnapshot snapshot,
      long limit,
      QuotaWindow window
  ) {
    long used = snapshot.exists() ? getLong(snapshot, "count") : 0;
    long remaining = Math.max(limit - used, 0);
    return new QuotaWindowStatus(limit, used, remaining, window.resetAt());
  }

  private String idempotencyId(String apiKeyId, String requestId) {
    return HashingUtils.sha256Hex(apiKeyId + ":" + requestId);
  }

  private DocumentReference idempotencyRef(String id) {
    return firestore.collection(properties.getCollections().getIdempotencyQuota()).document(id);
  }

  private DocumentReference windowRef(String apiKeyId, QuotaWindow window) {
    return windowRef(apiKeyId, window.type(), window.windowStart());
  }

  private DocumentReference windowRef(String apiKeyId, QuotaWindowType type, Instant windowStart) {
    String docId = apiKeyId + "_" + type.name() + "_" + windowStart.toEpochMilli();
    return firestore.collection(properties.getCollections().getQuotaWindows()).document(docId);
  }

  private WindowDecision buildWindowDecision(
      DocumentSnapshot snapshot,
      String apiKeyId,
      QuotaWindow window,
      long limit,
      int cost
  ) {
    long current = snapshot.exists() ? getLong(snapshot, "count") : 0;
    long newCount = current + cost;
    long remaining = Math.max(limit - newCount, 0);
    long overage = Math.max(newCount - limit, 0);
    boolean exceeded = newCount > limit;
    return new WindowDecision(apiKeyId, window.type(), window.windowStart(), limit, current, newCount, remaining,
        overage, window.resetAt(),
        exceeded);
  }

  private void persistWindow(
      Transaction transaction,
      DocumentReference ref,
      DocumentSnapshot snapshot,
      WindowDecision decision,
      Instant now
  ) {
    Map<String, Object> data = new HashMap<>();
    data.put("apiKeyId", decision.apiKeyId());
    data.put("windowType", decision.type().name());
    data.put("windowStart", toTimestamp(decision.windowStart()));
    data.put("count", decision.newCount());
    data.put("updatedAt", toTimestamp(now));
    if (!snapshot.exists()) {
      data.put("createdAt", toTimestamp(now));
    }
    transaction.set(ref, data, SetOptions.merge());
  }

  private void updateWindowCount(
      Transaction transaction,
      DocumentReference ref,
      DocumentSnapshot snapshot,
      String apiKeyId,
      QuotaWindowType type,
      Instant windowStart,
      int cost,
      Instant now
  ) {
    long current = snapshot.exists() ? getLong(snapshot, "count") : 0;
    long newCount = Math.max(current - cost, 0);

    Map<String, Object> data = new HashMap<>();
    data.put("apiKeyId", apiKeyId);
    data.put("windowType", type.name());
    data.put("windowStart", toTimestamp(windowStart));
    data.put("count", newCount);
    data.put("updatedAt", toTimestamp(now));
    if (!snapshot.exists()) {
      data.put("createdAt", toTimestamp(now));
    }
    transaction.set(ref, data, SetOptions.merge());
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
      QuotaWindow minuteWindow,
      QuotaWindow dayWindow,
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
    data.put("minuteWindowStart", toTimestamp(minuteWindow.windowStart()));
    data.put("dayWindowStart", toTimestamp(dayWindow.windowStart()));
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
      String apiKeyId,
      QuotaWindowType type,
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
