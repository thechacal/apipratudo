package com.apipratudo.quota.repository;

import com.apipratudo.quota.config.FirestoreProperties;
import com.apipratudo.quota.dto.ApiKeyLimits;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.ApiKeyStatus;
import com.apipratudo.quota.model.Plan;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(Firestore.class)
public class FirestoreApiKeyRepository implements ApiKeyRepository {

  private final Firestore firestore;
  private final FirestoreProperties properties;

  public FirestoreApiKeyRepository(Firestore firestore, FirestoreProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public ApiKey save(ApiKey apiKey) {
    Map<String, Object> data = toDocument(apiKey);
    try {
      firestore.collection(collection())
          .document(apiKey.id())
          .set(data)
          .get();
      return apiKey;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ApiKey save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save api key", e);
    }
  }

  @Override
  public Optional<ApiKey> findById(String id) {
    try {
      DocumentSnapshot snapshot = firestore.collection(collection())
          .document(id)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromSnapshot(snapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ApiKey lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch api key", e);
    }
  }

  @Override
  public Optional<ApiKey> findByApiKeyHash(String apiKeyHash) {
    try {
      QuerySnapshot snapshot = firestore.collection(collection())
          .whereEqualTo("apiKeyHash", apiKeyHash)
          .limit(1)
          .get()
          .get();
      if (snapshot.isEmpty()) {
        return Optional.empty();
      }
      DocumentSnapshot doc = snapshot.getDocuments().get(0);
      return Optional.ofNullable(fromSnapshot(doc));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ApiKey lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch api key", e);
    }
  }

  private String collection() {
    return properties.getCollections().getApiKeys();
  }

  private Map<String, Object> toDocument(ApiKey apiKey) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", apiKey.id());
    data.put("apiKeyHash", apiKey.apiKeyHash());
    data.put("name", apiKey.name());
    data.put("owner", apiKey.owner());
    data.put("ownerEmail", apiKey.ownerEmail());
    data.put("orgName", apiKey.orgName());
    data.put("status", apiKey.status().name());
    data.put("plan", apiKey.plan() == null ? Plan.FREE.name() : apiKey.plan().name());
    data.put("createdAt", toTimestamp(apiKey.createdAt()));
    data.put("minuteBucket", toTimestamp(apiKey.minuteBucket()));
    data.put("minuteCount", apiKey.minuteCount());
    data.put("dayBucket", apiKey.dayBucket());
    data.put("dayCount", apiKey.dayCount());

    ApiKeyLimits limits = apiKey.limits();
    Map<String, Object> limitsData = new HashMap<>();
    limitsData.put("requestsPerMinute", limits.requestsPerMinute());
    limitsData.put("requestsPerDay", limits.requestsPerDay());
    data.put("limits", limitsData);

    return data;
  }

  private ApiKey fromSnapshot(DocumentSnapshot snapshot) {
    String id = snapshot.getString("id");
    if (id == null) {
      id = snapshot.getId();
    }

    String apiKeyHash = snapshot.getString("apiKeyHash");
    String name = snapshot.getString("name");
    String owner = snapshot.getString("owner");

    String statusRaw = snapshot.getString("status");
    ApiKeyStatus status = statusRaw == null ? ApiKeyStatus.ACTIVE : toStatus(statusRaw);

    String planRaw = snapshot.getString("plan");
    Plan plan = planRaw == null ? Plan.FREE : Plan.valueOf(planRaw);

    @SuppressWarnings("unchecked")
    Map<String, Object> limitsRaw = snapshot.get("limits") instanceof Map<?, ?>
        ? (Map<String, Object>) snapshot.get("limits")
        : null;
    ApiKeyLimits limits = new ApiKeyLimits(
        toInt(limitsRaw, "requestsPerMinute"),
        toInt(limitsRaw, "requestsPerDay")
    );

    Instant createdAt = toInstant(snapshot.getTimestamp("createdAt"));
    Instant minuteBucket = toInstant(snapshot.getTimestamp("minuteBucket"));
    long minuteCount = getLong(snapshot, "minuteCount");
    String dayBucket = snapshot.getString("dayBucket");
    long dayCount = getLong(snapshot, "dayCount");
    return new ApiKey(id, apiKeyHash, name, owner, snapshot.getString("ownerEmail"),
        snapshot.getString("orgName"), limits, createdAt, status, plan, minuteBucket, minuteCount, dayBucket,
        dayCount);
  }

  private Instant toInstant(Timestamp timestamp) {
    if (timestamp == null) {
      return null;
    }
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  private Timestamp toTimestamp(Instant instant) {
    if (instant == null) {
      return null;
    }
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
  }

  private int toInt(Map<String, Object> data, String key) {
    if (data == null) {
      return 0;
    }
    Object value = data.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    return 0;
  }

  private long getLong(DocumentSnapshot snapshot, String field) {
    Long value = snapshot.getLong(field);
    return value == null ? 0 : value;
  }

  private ApiKeyStatus toStatus(String statusRaw) {
    if ("DISABLED".equalsIgnoreCase(statusRaw)) {
      return ApiKeyStatus.SUSPENDED;
    }
    return ApiKeyStatus.valueOf(statusRaw);
  }
}
