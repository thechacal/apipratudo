package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.gateway.config.DeliveryProperties;
import com.apipratudo.gateway.config.WebhookProperties;
import com.apipratudo.gateway.webhook.model.Delivery;
import com.apipratudo.gateway.webhook.model.DeliveryStatus;
import com.apipratudo.gateway.webhook.model.Webhook;
import com.apipratudo.gateway.webhook.model.WebhookStatus;
import com.apipratudo.gateway.webhook.repo.DeliveryRepository;
import com.apipratudo.gateway.webhook.repo.WebhookRepository;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "app.firestore.enabled=true",
    "app.firestore.project-id=emulator-test",
    "app.webhooks.collection=webhooks_test",
    "app.deliveries.collection=deliveries_test"
})
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class FirestoreEmulatorIntegrationTest {

  @Autowired
  private Firestore firestore;

  @Autowired
  private WebhookRepository webhookRepository;

  @Autowired
  private DeliveryRepository deliveryRepository;

  @Autowired
  private WebhookProperties webhookProperties;

  @Autowired
  private DeliveryProperties deliveryProperties;

  @BeforeEach
  void setup() {
    clearCollections();
  }

  @AfterEach
  void teardown() {
    clearCollections();
  }

  @Test
  @Timeout(10)
  void saveAndFetchWebhookAndDelivery() {
    String webhookId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Webhook webhook = new Webhook(
        webhookId,
        "https://cliente.exemplo.com/webhooks/apipratudo",
        "invoice.paid",
        WebhookStatus.ACTIVE,
        now,
        now
    );

    webhookRepository.save(webhook);
    Webhook loaded = webhookRepository.findById(webhookId).orElseThrow();
    assertThat(loaded.targetUrl()).isEqualTo(webhook.targetUrl());
    assertThat(loaded.eventType()).isEqualTo(webhook.eventType());

    String deliveryId = UUID.randomUUID().toString();
    Delivery delivery = new Delivery(
        deliveryId,
        webhookId,
        webhook.eventType(),
        webhook.targetUrl(),
        DeliveryStatus.SUCCESS,
        1,
        200,
        now
    );

    deliveryRepository.save(delivery);
    Delivery loadedDelivery = deliveryRepository.findById(deliveryId).orElseThrow();
    assertThat(loadedDelivery.webhookId()).isEqualTo(webhookId);

    List<Delivery> deliveries = deliveryRepository.findByWebhookId(webhookId);
    assertThat(deliveries).anyMatch(item -> item.id().equals(deliveryId));
  }

  private void clearCollections() {
    clearCollection(webhookProperties.getCollection());
    clearCollection(deliveryProperties.getCollection());
  }

  private void clearCollection(String collection) {
    try {
      List<? extends DocumentSnapshot> docs = firestore.collection(collection).get().get().getDocuments();
      for (DocumentSnapshot doc : docs) {
        doc.getReference().delete().get();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Firestore cleanup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to cleanup Firestore emulator data", e);
    }
  }
}
