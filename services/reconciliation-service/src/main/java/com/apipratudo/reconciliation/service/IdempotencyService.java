package com.apipratudo.reconciliation.service;

import com.apipratudo.reconciliation.config.IdempotencyProperties;
import com.apipratudo.reconciliation.model.IdempotencyRecord;
import com.apipratudo.reconciliation.repository.ReconciliationStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

  private final ReconciliationStore store;
  private final IdempotencyProperties properties;
  private final ObjectMapper objectMapper;

  public IdempotencyService(ReconciliationStore store, IdempotencyProperties properties, ObjectMapper objectMapper) {
    this.store = store;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public IdempotencyResult execute(String tenantId, String endpointKey, String idempotencyKey,
                                   Supplier<Object> supplier) {
    String key = tenantId + ":" + endpointKey + ":" + idempotencyKey;
    IdempotencyRecord existing = store.getIdempotency(key);
    if (existing != null) {
      return new IdempotencyResult(true, existing.statusCode(), readJson(existing.bodyJson()));
    }
    Object body = supplier.get();
    String json = writeJson(body);
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(properties.getTtlSeconds());
    store.saveIdempotency(new IdempotencyRecord(key, 200, json, now, expiresAt));
    return new IdempotencyResult(false, 200, body);
  }

  private JsonNode readJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to parse idempotency json", ex);
    }
  }

  private String writeJson(Object body) {
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialize idempotency json", ex);
    }
  }

  public record IdempotencyResult(boolean replayed, int statusCode, Object body) {
  }
}
