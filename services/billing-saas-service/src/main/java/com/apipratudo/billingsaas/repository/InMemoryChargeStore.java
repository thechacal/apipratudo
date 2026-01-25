package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.model.Charge;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(FirestoreChargeStore.class)
public class InMemoryChargeStore implements ChargeStore {

  private final ConcurrentMap<String, Map<String, Charge>> store = new ConcurrentHashMap<>();

  @Override
  public Charge save(String tenantId, Charge charge) {
    store.computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>())
        .put(charge.id(), charge);
    return charge;
  }

  @Override
  public Optional<Charge> findById(String tenantId, String id) {
    Map<String, Charge> tenantMap = store.get(tenantId);
    if (tenantMap == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(tenantMap.get(id));
  }

  @Override
  public Optional<Charge> findByProviderChargeId(String providerChargeId) {
    if (providerChargeId == null) {
      return Optional.empty();
    }
    for (Map<String, Charge> tenantMap : store.values()) {
      for (Charge charge : tenantMap.values()) {
        if (providerChargeId.equals(charge.providerChargeId())) {
          return Optional.of(charge);
        }
      }
    }
    return Optional.empty();
  }

  @Override
  public List<Charge> findByCreatedAtBetween(String tenantId, Instant start, Instant end) {
    Map<String, Charge> tenantMap = store.get(tenantId);
    if (tenantMap == null) {
      return List.of();
    }
    List<Charge> results = new ArrayList<>();
    for (Charge charge : tenantMap.values()) {
      Instant createdAt = charge.createdAt();
      if (createdAt == null) {
        continue;
      }
      if (!createdAt.isBefore(start) && !createdAt.isAfter(end)) {
        results.add(charge);
      }
    }
    return results;
  }
}
