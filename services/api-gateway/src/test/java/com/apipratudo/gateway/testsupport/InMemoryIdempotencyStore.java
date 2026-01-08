package com.apipratudo.gateway.testsupport;

import com.apipratudo.gateway.idempotency.HashingUtils;
import com.apipratudo.gateway.idempotency.IdempotencyOperation;
import com.apipratudo.gateway.idempotency.IdempotencyRequest;
import com.apipratudo.gateway.idempotency.IdempotencyResponse;
import com.apipratudo.gateway.idempotency.IdempotencyResult;
import com.apipratudo.gateway.idempotency.IdempotencyStore;
import com.apipratudo.gateway.idempotency.IdempotencyTransaction;
import com.apipratudo.gateway.webhook.IdempotencyConflictException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class InMemoryIdempotencyStore implements IdempotencyStore {

  private final ConcurrentMap<String, StoredRecord> store = new ConcurrentHashMap<>();

  @Override
  public IdempotencyResult execute(IdempotencyRequest request, IdempotencyOperation operation) {
    String docId = HashingUtils.sha256Hex(request.method() + " " + request.path() + " " + request.idempotencyKey());
    AtomicReference<IdempotencyResult> resultRef = new AtomicReference<>();

    store.compute(docId, (ignored, existing) -> {
      if (existing == null) {
        IdempotencyResponse response = operation.execute(NoopTransaction.INSTANCE);
        StoredRecord created = new StoredRecord(request.requestHash(), response);
        resultRef.set(new IdempotencyResult(
            response.statusCode(),
            response.responseBodyJson(),
            response.responseHeaders(),
            false
        ));
        return created;
      }

      if (!existing.requestHash.equals(request.requestHash())) {
        throw new IdempotencyConflictException(request.idempotencyKey());
      }

      Map<String, String> headers = existing.response.responseHeaders();
      if (headers == null) {
        headers = Collections.emptyMap();
      }

      resultRef.set(new IdempotencyResult(
          existing.response.statusCode(),
          existing.response.responseBodyJson(),
          headers,
          true
      ));

      return existing;
    });

    return resultRef.get();
  }

  private record StoredRecord(String requestHash, IdempotencyResponse response) {
  }

  private enum NoopTransaction implements IdempotencyTransaction {
    INSTANCE;

    @Override
    public void set(String collection, String documentId, Map<String, Object> data) {
      // no-op for tests
    }
  }
}
