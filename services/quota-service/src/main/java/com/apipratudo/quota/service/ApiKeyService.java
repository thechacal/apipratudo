package com.apipratudo.quota.service;

import com.apipratudo.quota.dto.ApiKeyCreateRequest;
import com.apipratudo.quota.dto.ApiKeyCreateResponse;
import com.apipratudo.quota.dto.ApiKeyResponse;
import com.apipratudo.quota.dto.ApiKeyRotateResponse;
import com.apipratudo.quota.error.ResourceNotFoundException;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.ApiKeyStatus;
import com.apipratudo.quota.repository.ApiKeyRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyService {

  private final ApiKeyRepository repository;
  private final Clock clock;

  public ApiKeyService(ApiKeyRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public ApiKeyCreateResponse create(ApiKeyCreateRequest request) {
    String id = UUID.randomUUID().toString();
    String apiKey = UUID.randomUUID().toString().replace("-", "");
    Instant now = Instant.now(clock);

    ApiKey model = new ApiKey(
        id,
        HashingUtils.sha256Hex(apiKey),
        request.name(),
        request.owner(),
        request.limits(),
        now,
        ApiKeyStatus.ACTIVE
    );
    repository.save(model);

    return new ApiKeyCreateResponse(id, apiKey, request.name(), request.owner(), request.limits(), now);
  }

  public ApiKeyResponse get(String id) {
    ApiKey apiKey = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("API key not found"));
    return new ApiKeyResponse(apiKey.id(), apiKey.name(), apiKey.owner(), apiKey.limits(), apiKey.createdAt(),
        apiKey.status());
  }

  public ApiKeyRotateResponse rotate(String id) {
    ApiKey apiKey = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("API key not found"));
    String newKey = UUID.randomUUID().toString().replace("-", "");
    ApiKey updated = new ApiKey(
        apiKey.id(),
        HashingUtils.sha256Hex(newKey),
        apiKey.name(),
        apiKey.owner(),
        apiKey.limits(),
        apiKey.createdAt(),
        apiKey.status()
    );
    repository.save(updated);
    return new ApiKeyRotateResponse(apiKey.id(), newKey);
  }

  public ApiKeyResponse updateStatus(String id, ApiKeyStatus status) {
    ApiKey apiKey = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("API key not found"));
    ApiKey updated = new ApiKey(
        apiKey.id(),
        apiKey.apiKeyHash(),
        apiKey.name(),
        apiKey.owner(),
        apiKey.limits(),
        apiKey.createdAt(),
        status
    );
    repository.save(updated);
    return new ApiKeyResponse(updated.id(), updated.name(), updated.owner(), updated.limits(), updated.createdAt(),
        updated.status());
  }
}
