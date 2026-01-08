package com.apipratudo.gateway.webhook.repo;

import com.apipratudo.gateway.idempotency.IdempotencyTransaction;
import com.apipratudo.gateway.webhook.model.Webhook;
import java.util.List;
import java.util.Optional;

public interface WebhookRepository {

  Webhook save(Webhook webhook);

  default Webhook save(Webhook webhook, IdempotencyTransaction transaction) {
    return save(webhook);
  }

  Optional<Webhook> findById(String id);

  List<Webhook> findAll();
}
