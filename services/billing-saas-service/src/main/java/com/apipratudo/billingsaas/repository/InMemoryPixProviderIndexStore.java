package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.model.PixProviderIndex;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(FirestorePixProviderIndexStore.class)
public class InMemoryPixProviderIndexStore implements PixProviderIndexStore {

  private final ConcurrentMap<String, PixProviderIndex> store = new ConcurrentHashMap<>();

  @Override
  public PixProviderIndex save(PixProviderIndex index) {
    store.put(index.providerChargeId(), index);
    return index;
  }

  @Override
  public Optional<PixProviderIndex> findByProviderChargeId(String providerChargeId) {
    if (providerChargeId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(store.get(providerChargeId));
  }
}
