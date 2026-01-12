package com.apipratudo.quota.repository;

import com.apipratudo.quota.model.ApiKey;
import java.util.Optional;

public interface ApiKeyRepository {

  ApiKey save(ApiKey apiKey);

  Optional<ApiKey> findById(String id);

  Optional<ApiKey> findByApiKeyHash(String apiKeyHash);
}
