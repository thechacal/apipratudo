package com.apipratudo.identity.service;

import com.apipratudo.identity.config.IdempotencyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

  private final Map<String, Entry> store = new ConcurrentHashMap<>();
  private final IdempotencyProperties properties;
  private final ObjectMapper objectMapper;

  public IdempotencyService(IdempotencyProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public IdempotencyResult execute(String tenantId, String endpointKey, String idempotencyKey, Supplier<Object> supplier) {
    String key = tenantId + ":" + endpointKey + ":" + idempotencyKey;
    Instant now = Instant.now();
    Entry existing = store.get(key);
    if (existing != null && existing.expiresAt().isAfter(now)) {
      return new IdempotencyResult(true, existing.statusCode(), readJson(existing.bodyJson()));
    }

    Object body = supplier.get();
    String json = writeJson(body);
    store.put(key, new Entry(200, json, now.plusSeconds(properties.getTtlSeconds())));
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

  private record Entry(int statusCode, String bodyJson, Instant expiresAt) {
  }

  public record IdempotencyResult(boolean replayed, int statusCode, Object body) {
  }
}
