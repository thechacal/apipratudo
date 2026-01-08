package com.apipratudo.gateway;

import com.apipratudo.gateway.idempotency.IdempotencyStore;
import com.apipratudo.gateway.testsupport.InMemoryIdempotencyStore;
import com.apipratudo.gateway.testsupport.InMemoryWebhookRepository;
import com.apipratudo.gateway.webhook.storage.WebhookRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestSupportConfig {

  @Bean
  public IdempotencyStore idempotencyStore() {
    return new InMemoryIdempotencyStore();
  }

  @Bean
  public WebhookRepository webhookRepository() {
    return new InMemoryWebhookRepository();
  }
}
