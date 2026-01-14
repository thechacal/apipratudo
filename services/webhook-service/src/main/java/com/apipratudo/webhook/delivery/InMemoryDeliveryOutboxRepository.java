package com.apipratudo.webhook.delivery;

import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(Firestore.class)
public class InMemoryDeliveryOutboxRepository implements DeliveryOutboxRepository {

  private final ConcurrentMap<String, OutboundDelivery> store = new ConcurrentHashMap<>();

  @Override
  public OutboundDelivery save(OutboundDelivery delivery) {
    store.put(delivery.id(), delivery);
    return delivery;
  }

  @Override
  public Optional<OutboundDelivery> findById(String id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<OutboundDelivery> findDue(Instant now, int limit) {
    return store.values().stream()
        .filter(delivery -> delivery.status() == OutboundDeliveryStatus.PENDING)
        .filter(delivery -> delivery.nextRetryAt() != null && !delivery.nextRetryAt().isAfter(now))
        .sorted(Comparator.comparing(OutboundDelivery::nextRetryAt).thenComparing(OutboundDelivery::createdAt))
        .limit(limit)
        .collect(Collectors.toList());
  }

  @Override
  public List<OutboundDelivery> findAll() {
    return store.values().stream()
        .sorted(Comparator.comparing(OutboundDelivery::createdAt).reversed())
        .collect(Collectors.toList());
  }

  @Override
  public void deleteAll() {
    store.clear();
  }
}
