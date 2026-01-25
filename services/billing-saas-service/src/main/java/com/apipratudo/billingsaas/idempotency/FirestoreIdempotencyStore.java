package com.apipratudo.billingsaas.idempotency;

import com.apipratudo.billingsaas.config.FirestoreProperties;
import com.apipratudo.billingsaas.config.IdempotencyProperties;
import com.apipratudo.billingsaas.error.IdempotencyConflictException;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(Firestore.class)
public class FirestoreIdempotencyStore implements IdempotencyStore {

  private final Firestore firestore;
  private final FirestoreProperties firestoreProperties;
  private final IdempotencyProperties idempotencyProperties;
  private final Clock clock;

  public FirestoreIdempotencyStore(
      Firestore firestore,
      FirestoreProperties firestoreProperties,
      IdempotencyProperties idempotencyProperties,
      Clock clock
  ) {
    this.firestore = firestore;
    this.firestoreProperties = firestoreProperties;
    this.idempotencyProperties = idempotencyProperties;
    this.clock = clock;
  }

  @Override
  public IdempotencyResult execute(IdempotencyRequest request, IdempotencyOperation operation) {
    String docId = HashingUtils.sha256Hex(
        request.tenantId() + " " + request.method() + " " + request.path() + " " + request.idempotencyKey());
    DocumentReference docRef = firestore.collection(firestoreProperties.getCollections().getIdempotency())
        .document(docId);

    try {
      return firestore.runTransaction(transaction -> handleTransaction(transaction, docRef, request, operation)).get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Failed to execute idempotency transaction", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Idempotency transaction interrupted", e);
    }
  }

  private IdempotencyResult handleTransaction(
      Transaction transaction,
      DocumentReference docRef,
      IdempotencyRequest request,
      IdempotencyOperation operation
  ) throws Exception {
    DocumentSnapshot snapshot = transaction.get(docRef).get();
    if (snapshot.exists()) {
      return replayOrConflict(snapshot, request);
    }

    IdempotencyResponse response = operation.execute(new FirestoreIdempotencyTransaction(firestore, transaction));
    Map<String, Object> data = buildDocument(request, response);
    transaction.set(docRef, data);

    return new IdempotencyResult(
        response.statusCode(),
        response.responseBodyJson(),
        response.responseHeaders(),
        false
    );
  }

  private IdempotencyResult replayOrConflict(DocumentSnapshot snapshot, IdempotencyRequest request) {
    String storedHash = snapshot.getString("requestHash");
    if (storedHash == null || !storedHash.equals(request.requestHash())) {
      throw new IdempotencyConflictException(request.idempotencyKey());
    }

    Long statusCode = snapshot.getLong("statusCode");
    String bodyJson = snapshot.getString("responseBodyJson");
    Map<String, String> headers = readHeaders(snapshot.get("responseHeaders"));

    return new IdempotencyResult(
        statusCode == null ? 200 : statusCode.intValue(),
        bodyJson == null ? "" : bodyJson,
        headers,
        true
    );
  }

  private Map<String, Object> buildDocument(IdempotencyRequest request, IdempotencyResponse response) {
    Instant now = Instant.now(clock);
    Instant expiresAt = now.plusSeconds(idempotencyProperties.getTtlSeconds());

    Map<String, Object> data = new HashMap<>();
    data.put("tenantId", request.tenantId());
    data.put("idempotencyKey", request.idempotencyKey());
    data.put("requestHash", request.requestHash());
    data.put("statusCode", response.statusCode());
    data.put("responseBodyJson", response.responseBodyJson());
    if (response.responseHeaders() != null && !response.responseHeaders().isEmpty()) {
      data.put("responseHeaders", response.responseHeaders());
    }
    data.put("createdAt", Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), now.getNano()));
    data.put("expiresAt", Timestamp.ofTimeSecondsAndNanos(expiresAt.getEpochSecond(), expiresAt.getNano()));

    return Collections.unmodifiableMap(data);
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> readHeaders(Object raw) {
    if (raw instanceof Map<?, ?> map) {
      Map<String, String> headers = new HashMap<>();
      map.forEach((key, value) -> {
        if (key != null && value != null) {
          headers.put(key.toString(), value.toString());
        }
      });
      return headers;
    }
    return Collections.emptyMap();
  }

  private static final class FirestoreIdempotencyTransaction implements IdempotencyTransaction {

    private final Firestore firestore;
    private final Transaction transaction;

    private FirestoreIdempotencyTransaction(Firestore firestore, Transaction transaction) {
      this.firestore = firestore;
      this.transaction = transaction;
    }

    @Override
    public void set(String collection, String documentId, Map<String, Object> data) {
      transaction.set(firestore.collection(collection).document(documentId), data);
    }
  }
}
