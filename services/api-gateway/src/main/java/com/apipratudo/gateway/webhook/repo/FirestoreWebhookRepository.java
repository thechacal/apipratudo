package com.apipratudo.gateway.webhook.repo;

import com.apipratudo.gateway.config.WebhookProperties;
import com.apipratudo.gateway.idempotency.IdempotencyTransaction;
import com.apipratudo.gateway.webhook.model.Webhook;
import com.apipratudo.gateway.webhook.model.WebhookStatus;
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
public class FirestoreWebhookRepository implements WebhookRepository {

  private final Firestore firestore;
  private final WebhookProperties properties;

  public FirestoreWebhookRepository(Firestore firestore, WebhookProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public Webhook save(Webhook webhook) {
    Map<String, Object> data = toDocument(webhook);
    try {
      firestore.collection(properties.getCollection())
          .document(webhook.id())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Webhook save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save webhook", e);
    }
    return webhook;
  }

  @Override
  public Webhook save(Webhook webhook, IdempotencyTransaction transaction) {
    Map<String, Object> data = toDocument(webhook);
    transaction.set(properties.getCollection(), webhook.id(), data);
    return webhook;
  }

  @Override
  public Optional<Webhook> findById(String id) {
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
      throw new IllegalStateException("Webhook lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch webhook", e);
    }
  }

  @Override
  public List<Webhook> findAll() {
    try {
      QuerySnapshot snapshot = firestore.collection(properties.getCollection())
          .orderBy("createdAt", Query.Direction.DESCENDING)
          .limit(properties.getListLimit())
          .get()
          .get();
      List<Webhook> webhooks = new ArrayList<>();
      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        Webhook webhook = fromSnapshot(doc);
        if (webhook != null) {
          webhooks.add(webhook);
        }
      }
      return webhooks;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Webhook list interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to list webhooks", e);
    }
  }

  private Map<String, Object> toDocument(Webhook webhook) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", webhook.id());
    data.put("targetUrl", webhook.targetUrl());
    data.put("eventType", webhook.eventType());
    data.put("status", webhook.status().name());
    data.put("createdAt", toTimestamp(webhook.createdAt()));
    data.put("updatedAt", toTimestamp(webhook.updatedAt()));
    return data;
  }

  private Webhook fromSnapshot(DocumentSnapshot snapshot) {
    String id = snapshot.getString("id");
    if (id == null) {
      id = snapshot.getId();
    }
    String targetUrl = snapshot.getString("targetUrl");
    String eventType = snapshot.getString("eventType");
    String statusRaw = snapshot.getString("status");
    WebhookStatus status = statusRaw == null ? WebhookStatus.ACTIVE : WebhookStatus.valueOf(statusRaw);
    Instant createdAt = toInstant(snapshot.getTimestamp("createdAt"));
    Instant updatedAt = toInstant(snapshot.getTimestamp("updatedAt"));

    if (targetUrl == null || eventType == null) {
      return null;
    }

    return new Webhook(
        id,
        targetUrl,
        eventType,
        status,
        createdAt,
        updatedAt
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
}
