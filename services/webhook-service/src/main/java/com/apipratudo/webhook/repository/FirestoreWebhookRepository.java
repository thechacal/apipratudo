package com.apipratudo.webhook.repository;

import com.apipratudo.webhook.config.FirestoreProperties;
import com.apipratudo.webhook.model.Webhook;
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
import org.springframework.util.StringUtils;

@Repository
@ConditionalOnBean(Firestore.class)
public class FirestoreWebhookRepository implements WebhookRepository {

  private final Firestore firestore;
  private final FirestoreProperties properties;

  public FirestoreWebhookRepository(Firestore firestore, FirestoreProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public Optional<Webhook> findByApiKeyAndIdempotencyKey(String apiKey, String idempotencyKey) {
    if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(idempotencyKey)) {
      return Optional.empty();
    }

    ApiFuture<QuerySnapshot> future = firestore.collection(collection())
        .whereEqualTo("apiKey", apiKey)
        .whereEqualTo("idempotencyKey", idempotencyKey)
        .limit(1)
        .get();

    try {
      List<? extends DocumentSnapshot> docs = future.get().getDocuments();
      if (docs.isEmpty()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromSnapshot(docs.get(0)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Webhook lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to lookup webhook", e);
    }
  }

  @Override
  public Optional<Webhook> findById(String id) {
    try {
      DocumentSnapshot snapshot = firestore.collection(collection()).document(id).get().get();
      return Optional.ofNullable(fromSnapshot(snapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Webhook lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to lookup webhook", e);
    }
  }

  @Override
  public Page listByApiKey(String apiKey, int limit, String cursor) {
    Query query = firestore.collection(collection())
        .whereEqualTo("apiKey", apiKey)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(limit + 1);

    if (StringUtils.hasText(cursor)) {
      try {
        DocumentSnapshot cursorSnapshot = firestore.collection(collection()).document(cursor).get().get();
        if (cursorSnapshot.exists() && apiKey.equals(cursorSnapshot.getString("apiKey"))) {
          Timestamp createdAt = cursorSnapshot.getTimestamp("createdAt");
          if (createdAt != null) {
            query = query.startAfter(createdAt);
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Webhook cursor lookup interrupted", e);
      } catch (ExecutionException e) {
        throw new IllegalStateException("Failed to lookup webhook cursor", e);
      }
    }

    try {
      List<? extends DocumentSnapshot> docs = query.get().get().getDocuments();
      List<Webhook> items = new ArrayList<>();
      for (DocumentSnapshot doc : docs) {
        Webhook webhook = fromSnapshot(doc);
        if (webhook != null) {
          items.add(webhook);
        }
      }
      String nextCursor = null;
      if (items.size() > limit) {
        Webhook last = items.get(limit - 1);
        nextCursor = last.id();
        items = items.subList(0, limit);
      }
      return new Page(items, nextCursor);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Webhook list interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to list webhooks", e);
    }
  }

  @Override
  public Webhook save(Webhook webhook) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", webhook.id());
    data.put("apiKey", webhook.apiKey());
    data.put("targetUrl", webhook.targetUrl());
    data.put("events", webhook.events());
    data.put("secret", webhook.secret());
    data.put("enabled", webhook.enabled());
    data.put("createdAt", toTimestamp(webhook.createdAt()));
    data.put("updatedAt", toTimestamp(webhook.updatedAt()));
    data.put("idempotencyKey", webhook.idempotencyKey());

    ApiFuture<?> future = firestore.collection(collection())
        .document(webhook.id())
        .set(data, SetOptions.merge());

    waitFuture(future, "Webhook save interrupted", "Failed to save webhook");
    return webhook;
  }

  private String collection() {
    return properties.getCollections().getWebhooks();
  }

  private Webhook fromSnapshot(DocumentSnapshot snapshot) {
    if (!snapshot.exists()) {
      return null;
    }
    String id = snapshot.getString("id");
    String apiKey = snapshot.getString("apiKey");
    String targetUrl = snapshot.getString("targetUrl");
    @SuppressWarnings("unchecked")
    List<String> events = (List<String>) snapshot.get("events");
    String secret = snapshot.getString("secret");
    Boolean enabled = snapshot.getBoolean("enabled");
    Instant createdAt = toInstant(snapshot.getTimestamp("createdAt"));
    Instant updatedAt = toInstant(snapshot.getTimestamp("updatedAt"));
    String idempotencyKey = snapshot.getString("idempotencyKey");

    return new Webhook(
        id,
        apiKey,
        targetUrl,
        events == null ? List.of() : events,
        secret,
        enabled != null && enabled,
        createdAt,
        updatedAt,
        idempotencyKey
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
