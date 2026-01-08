package com.apipratudo.gateway.webhook.repo;

import com.apipratudo.gateway.webhook.model.Delivery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
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
  public List<Delivery> findAll() {
    return new ArrayList<>(store.values());
  }
}
