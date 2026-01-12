package com.apipratudo.quota.repository;

import com.apipratudo.quota.config.FirestoreProperties;
import com.apipratudo.quota.config.QuotaProperties;
import com.apipratudo.quota.dto.ApiKeyLimits;
import com.apipratudo.quota.dto.QuotaReason;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.QuotaDecision;
import com.apipratudo.quota.model.QuotaWindowType;
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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
  public QuotaDecision consume(ApiKey apiKey, String requestId, String route, int cost) {
    String idempotencyId = HashingUtils.sha256Hex(apiKey.id() + ":" + requestId);
    Instant now = Instant.now(clock);
    Instant minuteStart = windowStart(QuotaWindowType.MINUTE, now);
    Instant dayStart = windowStart(QuotaWindowType.DAY, now);
    DocumentReference idempotencyRef = firestore.collection(properties.getCollections().getIdempotencyQuota())
        .document(idempotencyId);

    DocumentReference minuteRef = windowRef(apiKey.id(), QuotaWindowType.MINUTE, minuteStart);
    DocumentReference dayRef = windowRef(apiKey.id(), QuotaWindowType.DAY, dayStart);

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

      WindowDecision minute = buildWindowDecision(minuteSnapshot, apiKey.id(), QuotaWindowType.MINUTE, minuteStart,
          minuteLimit, cost);
      WindowDecision day = buildWindowDecision(daySnapshot, apiKey.id(), QuotaWindowType.DAY, dayStart, dayLimit, cost);

      QuotaDecision decision;
      if (minute.exceeded() || day.exceeded()) {
        WindowDecision exceeded = chooseExceeded(minute, day);
        decision = new QuotaDecision(false, QuotaReason.RATE_LIMITED, exceeded.limit(), 0, exceeded.resetAt());
      } else {
        persistWindow(transaction, minuteRef, minuteSnapshot, minute, now);
        persistWindow(transaction, dayRef, daySnapshot, day, now);
        WindowDecision selected = chooseMostRestrictive(minute, day);
        decision = new QuotaDecision(true, null, selected.limit(), selected.remaining(), selected.resetAt());
      }

      Map<String, Object> idempotencyData = idempotencyData(apiKey, requestId, route, decision, now);
      transaction.set(idempotencyRef, idempotencyData, SetOptions.merge());
      return decision;
    });

    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Quota consume interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to consume quota", e);
    }
  }

  private DocumentReference windowRef(String apiKeyId, QuotaWindowType type, Instant windowStart) {
    String docId = apiKeyId + "_" + type.name() + "_" + windowStart.toEpochMilli();
    return firestore.collection(properties.getCollections().getQuotaWindows()).document(docId);
  }

  private WindowDecision buildWindowDecision(
      DocumentSnapshot snapshot,
      String apiKeyId,
      QuotaWindowType type,
      Instant windowStart,
      long limit,
      int cost
  ) {
    long current = snapshot.exists() ? getLong(snapshot, "count") : 0;
    long newCount = current + cost;
    long remaining = Math.max(limit - newCount, 0);
    long overage = Math.max(newCount - limit, 0);
    boolean exceeded = newCount > limit;
    Instant resetAt = windowStart.plus(type.duration());
    return new WindowDecision(apiKeyId, type, windowStart, limit, current, newCount, remaining, overage, resetAt,
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
      Instant now
  ) {
    Map<String, Object> data = new HashMap<>();
    data.put("apiKeyId", apiKey.id());
    data.put("requestId", requestId);
    data.put("route", route);
    data.put("allowed", decision.allowed());
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
    Instant resetAtInstant = resetAt == null
        ? null
        : Instant.ofEpochSecond(resetAt.getSeconds(), resetAt.getNanos());
    return new QuotaDecision(allowed, reason, limit, remaining, resetAtInstant);
  }

  private long getLong(DocumentSnapshot snapshot, String field) {
    Long value = snapshot.getLong(field);
    return value == null ? 0 : value;
  }

  private Instant windowStart(QuotaWindowType type, Instant now) {
    return switch (type) {
      case MINUTE -> now.truncatedTo(ChronoUnit.MINUTES);
      case DAY -> now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    };
  }

  private Timestamp toTimestamp(Instant instant) {
    if (instant == null) {
      return null;
    }
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
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
