package com.apipratudo.billingsaas.idempotency;

import com.apipratudo.billingsaas.error.IdempotencyConflictException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(Firestore.class)
public class InMemoryIdempotencyStore implements IdempotencyStore {

  private final ConcurrentMap<String, StoredRecord> store = new ConcurrentHashMap<>();

  @Override
  public IdempotencyResult execute(IdempotencyRequest request, IdempotencyOperation operation) {
    String docId = HashingUtils.sha256Hex(
        request.tenantId() + " " + request.method() + " " + request.path() + " " + request.idempotencyKey());
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
      // no-op for in-memory store
    }

    @Override
    public boolean isNoop() {
      return true;
    }
  }
}
