package com.apipratudo.gateway.webhook.repo;

import com.apipratudo.gateway.config.DeliveryProperties;
import com.apipratudo.gateway.webhook.model.Delivery;
import com.apipratudo.gateway.webhook.model.DeliveryAttempt;
import com.apipratudo.gateway.webhook.model.DeliveryStatus;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(Firestore.class)
public class FirestoreDeliveryRepository implements DeliveryRepository {

  private final Firestore firestore;
  private final DeliveryProperties properties;

  public FirestoreDeliveryRepository(Firestore firestore, DeliveryProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public Delivery save(Delivery delivery) {
    Map<String, Object> data = toDocument(delivery);
    try {
      firestore.collection(properties.getCollection())
          .document(delivery.id())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Delivery save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save delivery", e);
    }
    return delivery;
  }

  @Override
  public Optional<Delivery> findById(String id) {
    try {
      DocumentSnapshot snapshot = firestore.collection(properties.getCollection())
          .document(id)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromSnapshot(snapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Delivery lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch delivery", e);
    }
  }

  @Override
  public List<Delivery> findByWebhookId(String webhookId) {
    Query query = firestore.collection(properties.getCollection())
        .whereEqualTo("webhookId", webhookId)
        .limit(properties.getListLimit());
    return executeQuery(query, "Failed to list deliveries by webhookId");
  }

  @Override
  public List<Delivery> findAll() {
    Query query = firestore.collection(properties.getCollection())
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(properties.getListLimit());
    return executeQuery(query, "Failed to list deliveries");
  }

  private List<Delivery> executeQuery(Query query, String errorMessage) {
    try {
      QuerySnapshot snapshot = query.get().get();
      List<Delivery> deliveries = new ArrayList<>();
      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        Delivery delivery = fromSnapshot(doc);
        if (delivery != null) {
          deliveries.add(delivery);
        }
      }
      return deliveries;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Delivery list interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException(errorMessage, e);
    }
  }

  private Map<String, Object> toDocument(Delivery delivery) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", delivery.id());
    data.put("webhookId", delivery.webhookId());
    data.put("eventType", delivery.eventType());
    data.put("targetUrl", delivery.targetUrl());
    data.put("status", delivery.status().name());
    data.put("attempt", delivery.attempt());
    data.put("responseCode", delivery.responseCode());
    data.put("createdAt", toTimestamp(delivery.createdAt()));
    data.put("attempts", toAttemptList(delivery.attempts()));
    return data;
  }

  private Delivery fromSnapshot(DocumentSnapshot snapshot) {
    String id = snapshot.getString("id");
    if (id == null) {
      id = snapshot.getId();
    }
    String webhookId = snapshot.getString("webhookId");
    String eventType = snapshot.getString("eventType");
    String targetUrl = snapshot.getString("targetUrl");
    String statusRaw = snapshot.getString("status");
    DeliveryStatus status = resolveStatus(statusRaw);
    Long attempt = snapshot.getLong("attempt");
    Long responseCode = snapshot.getLong("responseCode");
    Instant createdAt = toInstant(snapshot.getTimestamp("createdAt"));
    List<DeliveryAttempt> attempts = toAttempts(snapshot.get("attempts"));

    if (webhookId == null || eventType == null || targetUrl == null) {
      return null;
    }

    return new Delivery(
        id,
        webhookId,
        eventType,
        targetUrl,
        status,
        attempt == null ? 1 : attempt.intValue(),
        responseCode == null ? 0 : responseCode.intValue(),
        createdAt,
        attempts
    );
  }

  private Timestamp toTimestamp(Instant instant) {
    if (instant == null) {
      return null;
    }
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
  }

  private Instant toInstant(Timestamp timestamp) {
    if (timestamp == null) {
      return Instant.EPOCH;
    }
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  private DeliveryStatus resolveStatus(String statusRaw) {
    if (statusRaw == null || statusRaw.isBlank()) {
      return DeliveryStatus.PENDING;
    }
    if ("SUCCESS".equalsIgnoreCase(statusRaw)) {
      return DeliveryStatus.DELIVERED;
    }
    return DeliveryStatus.valueOf(statusRaw);
  }

  private List<Map<String, Object>> toAttemptList(List<DeliveryAttempt> attempts) {
    if (attempts == null || attempts.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> payloads = new ArrayList<>();
    for (DeliveryAttempt attempt : attempts) {
      Map<String, Object> data = new HashMap<>();
      data.put("attempt", attempt.attempt());
      data.put("responseCode", attempt.responseCode());
      data.put("errorMessage", attempt.errorMessage());
      data.put("occurredAt", toTimestamp(attempt.occurredAt()));
      payloads.add(data);
    }
    return payloads;
  }

  private List<DeliveryAttempt> toAttempts(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<DeliveryAttempt> attempts = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        continue;
      }
      Object attemptValue = map.get("attempt");
      Object responseCodeValue = map.get("responseCode");
      Object errorMessageValue = map.get("errorMessage");
      Object occurredAtValue = map.get("occurredAt");

      Integer attempt = toInt(attemptValue);
      Integer responseCode = toInt(responseCodeValue);
      String errorMessage = errorMessageValue == null ? null : errorMessageValue.toString();
      Instant occurredAt = occurredAtValue instanceof Timestamp ts ? toInstant(ts) : null;

      if (attempt != null) {
        attempts.add(new DeliveryAttempt(attempt, responseCode, errorMessage, occurredAt));
      }
    }
    return attempts;
  }

  private Integer toInt(Object value) {
    if (value instanceof Long number) {
      return number.intValue();
    }
    if (value instanceof Integer number) {
      return number;
    }
    if (value instanceof String text) {
      try {
        return Integer.parseInt(text);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
