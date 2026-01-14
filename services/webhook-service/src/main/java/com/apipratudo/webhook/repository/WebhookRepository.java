package com.apipratudo.webhook.repository;

import com.apipratudo.webhook.model.Webhook;
import java.util.List;
import java.util.Optional;

public interface WebhookRepository {

  record Page(List<Webhook> items, String nextCursor) {
  }

  Optional<Webhook> findByApiKeyAndIdempotencyKey(String apiKey, String idempotencyKey);

  Optional<Webhook> findById(String id);

  Page listByApiKey(String apiKey, int limit, String cursor);

  Webhook save(Webhook webhook);
}
