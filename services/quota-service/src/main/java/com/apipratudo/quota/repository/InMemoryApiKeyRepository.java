package com.apipratudo.quota.repository;

import com.apipratudo.quota.model.ApiKey;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryApiKeyRepository implements ApiKeyRepository {

  private final ConcurrentMap<String, ApiKey> byId = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ApiKey> byHash = new ConcurrentHashMap<>();

  @Override
  public ApiKey save(ApiKey apiKey) {
    byId.put(apiKey.id(), apiKey);
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
