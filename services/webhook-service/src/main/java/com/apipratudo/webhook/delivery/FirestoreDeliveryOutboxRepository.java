package com.apipratudo.webhook.delivery;

import com.apipratudo.webhook.config.FirestoreProperties;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
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
public class FirestoreDeliveryOutboxRepository implements DeliveryOutboxRepository {

  private final Firestore firestore;
  private final FirestoreProperties properties;

  public FirestoreDeliveryOutboxRepository(Firestore firestore, FirestoreProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public OutboundDelivery save(OutboundDelivery delivery) {
    Map<String, Object> data = toDocument(delivery);
    ApiFuture<?> future = firestore.collection(collection())
        .document(delivery.id())
        .set(data, SetOptions.merge());
    waitFuture(future, "Outbox save interrupted", "Failed to save outbox delivery");
    return delivery;
  }

  @Override
  public Optional<OutboundDelivery> findById(String id) {
    try {
      DocumentSnapshot snapshot = firestore.collection(collection()).document(id).get().get();
      return Optional.ofNullable(fromSnapshot(snapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Outbox lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to lookup outbox delivery", e);
    }
  }

  @Override
  public List<OutboundDelivery> findDue(Instant now, int limit) {
    Query query = firestore.collection(collection())
        .whereEqualTo("status", OutboundDeliveryStatus.PENDING.name())
        .whereLessThanOrEqualTo("nextRetryAt", toTimestamp(now))
        .orderBy("nextRetryAt", Query.Direction.ASCENDING)
        .limit(limit);

    try {
      QuerySnapshot snapshot = query.get().get();
      List<OutboundDelivery> deliveries = new ArrayList<>();
      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        OutboundDelivery delivery = fromSnapshot(doc);
        if (delivery != null) {
          deliveries.add(delivery);
        }
      }
      return deliveries;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Outbox query interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to query outbox deliveries", e);
    }
  }

  @Override
  public List<OutboundDelivery> findAll() {
    try {
      QuerySnapshot snapshot = firestore.collection(collection()).get().get();
      List<OutboundDelivery> deliveries = new ArrayList<>();
      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        OutboundDelivery delivery = fromSnapshot(doc);
        if (delivery != null) {
          deliveries.add(delivery);
        }
      }
      return deliveries;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Outbox list interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to list outbox deliveries", e);
    }
  }

  @Override
  public void deleteAll() {
    List<OutboundDelivery> deliveries = findAll();
    for (OutboundDelivery delivery : deliveries) {
      firestore.collection(collection()).document(delivery.id()).delete();
    }
  }

  private String collection() {
    return properties.getCollections().getDeliveries();
  }

  private Map<String, Object> toDocument(OutboundDelivery delivery) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", delivery.id());
    data.put("webhookId", delivery.webhookId());
    data.put("apiKey", delivery.apiKey());
    data.put("deliveryId", delivery.deliveryId());
    data.put("event", delivery.event());
    data.put("targetUrl", delivery.targetUrl());
    data.put("secret", delivery.secret());
    data.put("payloadJson", delivery.payloadJson());
    data.put("status", delivery.status().name());
    data.put("attemptCount", delivery.attemptCount());
    data.put("nextRetryAt", toTimestamp(delivery.nextRetryAt()));
    data.put("lastStatusCode", delivery.lastStatusCode());
    data.put("lastError", delivery.lastError());
    data.put("createdAt", toTimestamp(delivery.createdAt()));
    data.put("updatedAt", toTimestamp(delivery.updatedAt()));
    return data;
  }

  private OutboundDelivery fromSnapshot(DocumentSnapshot snapshot) {
    if (!snapshot.exists()) {
      return null;
    }
    String id = snapshot.getString("id");
    String webhookId = snapshot.getString("webhookId");
    String apiKey = snapshot.getString("apiKey");
    String deliveryId = snapshot.getString("deliveryId");
    String event = snapshot.getString("event");
    String targetUrl = snapshot.getString("targetUrl");
    String secret = snapshot.getString("secret");
    String payloadJson = snapshot.getString("payloadJson");
    String statusRaw = snapshot.getString("status");
    Integer attemptCount = snapshot.getLong("attemptCount") == null ? 0 : snapshot.getLong("attemptCount").intValue();
    Instant nextRetryAt = toInstant(snapshot.getTimestamp("nextRetryAt"));
    Integer lastStatusCode = snapshot.getLong("lastStatusCode") == null ? null
        : snapshot.getLong("lastStatusCode").intValue();
    String lastError = snapshot.getString("lastError");
    Instant createdAt = toInstant(snapshot.getTimestamp("createdAt"));
    Instant updatedAt = toInstant(snapshot.getTimestamp("updatedAt"));

    OutboundDeliveryStatus status = statusRaw == null ? OutboundDeliveryStatus.PENDING
        : OutboundDeliveryStatus.valueOf(statusRaw);

    return new OutboundDelivery(
        id,
        webhookId,
        apiKey,
        deliveryId,
        event,
        targetUrl,
        secret,
        payloadJson,
        status,
        attemptCount,
        nextRetryAt,
        lastStatusCode,
        lastError,
        createdAt,
        updatedAt
    );
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

  private void waitFuture(ApiFuture<?> future, String interruptedMessage, String failedMessage) {
    try {
      future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interruptedMessage, e);
    } catch (ExecutionException e) {
      throw new IllegalStateException(failedMessage, e);
    }
  }
}
