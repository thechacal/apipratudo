package com.apipratudo.gateway.webhook.storage;

import com.apipratudo.gateway.idempotency.IdempotencyTransaction;

public interface WebhookRepository {

  void save(WebhookRecord record);

  void save(WebhookRecord record, IdempotencyTransaction transaction);
}
