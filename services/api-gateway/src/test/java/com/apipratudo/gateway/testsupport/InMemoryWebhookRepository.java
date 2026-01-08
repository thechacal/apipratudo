package com.apipratudo.gateway.testsupport;

import com.apipratudo.gateway.idempotency.IdempotencyTransaction;
import com.apipratudo.gateway.webhook.storage.WebhookRecord;
import com.apipratudo.gateway.webhook.storage.WebhookRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWebhookRepository implements WebhookRepository {

  private final Map<String, WebhookRecord> store = new ConcurrentHashMap<>();

  @Override
  public void save(WebhookRecord record) {
    store.put(record.id(), record);
  }

  @Override
  public void save(WebhookRecord record, IdempotencyTransaction transaction) {
    save(record);
  }
}
