package com.apipratudo.gateway.webhook.repo;

import com.apipratudo.gateway.webhook.model.Delivery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryDeliveryRepository implements DeliveryRepository {

  private final ConcurrentMap<String, Delivery> store = new ConcurrentHashMap<>();

  @Override
  public Delivery save(Delivery delivery) {
    store.put(delivery.id(), delivery);
    return delivery;
  }

  @Override
  public Optional<Delivery> findById(String id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Delivery> findByWebhookId(String webhookId) {
    List<Delivery> deliveries = new ArrayList<>();
    for (Delivery delivery : store.values()) {
      if (delivery.webhookId().equals(webhookId)) {
        deliveries.add(delivery);
      }
    }
    return deliveries;
  }

  @Override
  public List<Delivery> findAll() {
    return new ArrayList<>(store.values());
  }
}
