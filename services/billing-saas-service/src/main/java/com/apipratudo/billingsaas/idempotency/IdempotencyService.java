package com.apipratudo.billingsaas.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IdempotencyService {

  private final IdempotencyStore store;
  private final ObjectMapper objectMapper;

  public IdempotencyService(IdempotencyStore store, ObjectMapper objectMapper) {
    this.store = store;
    this.objectMapper = objectMapper;
  }

  public IdempotencyResult execute(
      String tenantId,
      String method,
      String path,
      String idempotencyKey,
      Object requestPayload,
      IdempotencyOperation operation
  ) {
    if (!StringUtils.hasText(idempotencyKey)) {
      IdempotencyResponse response = operation.execute(NoopTransaction.INSTANCE);
      return new IdempotencyResult(
          response.statusCode(),
          response.responseBodyJson(),
          response.responseHeaders() == null ? Collections.emptyMap() : response.responseHeaders(),
          false
      );
    }

    String requestHash = HashingUtils.sha256Hex(buildHashInput(tenantId, method, path, requestPayload));
    IdempotencyRequest request = new IdempotencyRequest(
        tenantId,
        method,
        path,
        idempotencyKey.trim(),
        requestHash
    );
    return store.execute(request, operation);
  }

  private String buildHashInput(String tenantId, String method, String path, Object payload) {
    String payloadJson = "";
    if (payload != null) {
      try {
        payloadJson = objectMapper.writeValueAsString(payload);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Failed to serialize request payload", e);
      }
    }
    return tenantId + "|" + method + "|" + path + "|" + payloadJson;
  }

  private enum NoopTransaction implements IdempotencyTransaction {
    INSTANCE;

    @Override
    public void set(String collection, String documentId, java.util.Map<String, Object> data) {
      // no-op
    }

    @Override
    public boolean isNoop() {
      return true;
    }
  }
}
