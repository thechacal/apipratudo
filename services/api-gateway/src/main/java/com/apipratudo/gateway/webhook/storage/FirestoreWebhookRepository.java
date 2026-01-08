package com.apipratudo.gateway.webhook.storage;

import com.apipratudo.gateway.config.WebhookProperties;
import com.apipratudo.gateway.idempotency.IdempotencyTransaction;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
  public void save(WebhookRecord record) {
    Map<String, Object> data = toDocument(record);
    try {
      firestore.collection(properties.getCollection())
          .document(record.id())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Webhook save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save webhook", e);
    }
  }

  @Override
  public void save(WebhookRecord record, IdempotencyTransaction transaction) {
    Map<String, Object> data = toDocument(record);
    transaction.set(properties.getCollection(), record.id(), data);
  }

  private Map<String, Object> toDocument(WebhookRecord record) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", record.id());
    data.put("targetUrl", record.targetUrl());
    data.put("eventType", record.eventType());
    data.put("status", record.status());
    data.put("createdAt", toTimestamp(record.createdAt()));
    return data;
  }

  private Timestamp toTimestamp(Instant instant) {
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
  }
}
