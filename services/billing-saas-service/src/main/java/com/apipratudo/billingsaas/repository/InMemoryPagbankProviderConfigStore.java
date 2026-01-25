package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.model.PagbankProviderConfig;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(FirestorePagbankProviderConfigStore.class)
public class InMemoryPagbankProviderConfigStore implements PagbankProviderConfigStore {

  private final ConcurrentMap<String, PagbankProviderConfig> store = new ConcurrentHashMap<>();

  @Override
  public PagbankProviderConfig save(String tenantId, PagbankProviderConfig config) {
    store.put(tenantId, config);
    return config;
  }

  @Override
  public Optional<PagbankProviderConfig> find(String tenantId) {
    if (tenantId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(store.get(tenantId));
  }

  @Override
  public void delete(String tenantId) {
    if (tenantId == null) {
      return;
    }
    store.remove(tenantId);
  }
}
