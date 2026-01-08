package com.apipratudo.gateway.webhook.repo;

import com.apipratudo.gateway.webhook.model.Webhook;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryWebhookRepository implements WebhookRepository {

  private final ConcurrentMap<String, Webhook> store = new ConcurrentHashMap<>();

  @Override
  public Webhook save(Webhook webhook) {
    store.put(webhook.id(), webhook);
    return webhook;
  }

  @Override
  public Optional<Webhook> findById(String id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Webhook> findAll() {
    return new ArrayList<>(store.values());
  }
}
