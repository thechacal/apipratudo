package com.apipratudo.webhook.repository;

import com.apipratudo.webhook.model.Webhook;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@ConditionalOnMissingBean(Firestore.class)
public class InMemoryWebhookRepository implements WebhookRepository {

  private final ConcurrentMap<String, Webhook> storage = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();

  @Override
  public Optional<Webhook> findByApiKeyAndIdempotencyKey(String apiKey, String idempotencyKey) {
    if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(idempotencyKey)) {
      return Optional.empty();
    }
    String key = indexKey(apiKey, idempotencyKey);
    String webhookId = idempotencyIndex.get(key);
    if (webhookId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(storage.get(webhookId));
  }

  @Override
  public Optional<Webhook> findById(String id) {
    return Optional.ofNullable(storage.get(id));
  }

  @Override
  public Page listByApiKey(String apiKey, int limit, String cursor) {
    List<Webhook> sorted = storage.values().stream()
        .filter(webhook -> apiKey.equals(webhook.apiKey()))
        .sorted(Comparator.comparing(Webhook::createdAt).reversed().thenComparing(Webhook::id))
        .collect(Collectors.toList());

    int startIndex = 0;
    if (StringUtils.hasText(cursor)) {
      for (int i = 0; i < sorted.size(); i++) {
        if (sorted.get(i).id().equals(cursor)) {
          startIndex = i + 1;
          break;
        }
      }
    }

    int endIndex = Math.min(startIndex + limit, sorted.size());
    List<Webhook> pageItems = sorted.subList(startIndex, endIndex);
    String nextCursor = endIndex < sorted.size() ? sorted.get(endIndex - 1).id() : null;
    return new Page(pageItems, nextCursor);
  }

  @Override
  public Webhook save(Webhook webhook) {
    storage.put(webhook.id(), webhook);
    if (StringUtils.hasText(webhook.idempotencyKey())) {
      idempotencyIndex.put(indexKey(webhook.apiKey(), webhook.idempotencyKey()), webhook.id());
    }
    return webhook;
  }

  private String indexKey(String apiKey, String idempotencyKey) {
    return apiKey + ":" + idempotencyKey;
  }
}
