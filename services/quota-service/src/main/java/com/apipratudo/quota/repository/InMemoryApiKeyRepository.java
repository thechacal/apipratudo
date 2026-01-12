package com.apipratudo.quota.repository;

import com.apipratudo.quota.model.ApiKey;
import com.google.cloud.firestore.Firestore;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(Firestore.class)
public class InMemoryApiKeyRepository implements ApiKeyRepository {

  private final ConcurrentMap<String, ApiKey> byId = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ApiKey> byHash = new ConcurrentHashMap<>();

  @Override
  public ApiKey save(ApiKey apiKey) {
    ApiKey existing = byId.put(apiKey.id(), apiKey);
    if (existing != null && !existing.apiKeyHash().equals(apiKey.apiKeyHash())) {
      byHash.remove(existing.apiKeyHash());
    }
    byHash.put(apiKey.apiKeyHash(), apiKey);
    return apiKey;
  }

  @Override
  public Optional<ApiKey> findById(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public Optional<ApiKey> findByApiKeyHash(String apiKeyHash) {
    return Optional.ofNullable(byHash.get(apiKeyHash));
  }
}
