package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.config.FirestoreProperties;
import com.apipratudo.billingsaas.idempotency.IdempotencyTransaction;
import com.apipratudo.billingsaas.model.PixProviderIndex;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(Firestore.class)
public class FirestorePixProviderIndexStore implements PixProviderIndexStore {

  private final Firestore firestore;
  private final FirestoreProperties properties;

  public FirestorePixProviderIndexStore(Firestore firestore, FirestoreProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public PixProviderIndex save(PixProviderIndex index) {
    Map<String, Object> data = toDocument(index);
    try {
      firestore.collection(properties.getCollections().getPixProviderIndex())
          .document(index.providerChargeId())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Pix provider index save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save pix provider index", e);
    }
    return index;
  }

  @Override
  public PixProviderIndex save(PixProviderIndex index, IdempotencyTransaction transaction) {
    Map<String, Object> data = toDocument(index);
    if (transaction.isNoop()) {
      return save(index);
    }
    transaction.set(properties.getCollections().getPixProviderIndex(), index.providerChargeId(), data);
    return index;
  }

  @Override
  public Optional<PixProviderIndex> findByProviderChargeId(String providerChargeId) {
    if (providerChargeId == null) {
      return Optional.empty();
    }
    try {
      DocumentSnapshot snapshot = firestore.collection(properties.getCollections().getPixProviderIndex())
          .document(providerChargeId)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromSnapshot(snapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Pix provider index lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch pix provider index", e);
    }
  }

  private Map<String, Object> toDocument(PixProviderIndex index) {
    Map<String, Object> data = new HashMap<>();
    data.put("providerChargeId", index.providerChargeId());
    data.put("tenantId", index.tenantId());
    data.put("chargeId", index.chargeId());
    data.put("createdAt", toTimestamp(index.createdAt()));
    return data;
  }

  private PixProviderIndex fromSnapshot(DocumentSnapshot snapshot) {
    String providerChargeId = snapshot.getString("providerChargeId");
    if (providerChargeId == null) {
      providerChargeId = snapshot.getId();
    }
    String tenantId = snapshot.getString("tenantId");
    String chargeId = snapshot.getString("chargeId");
    Instant createdAt = toInstant(snapshot.getTimestamp("createdAt"));
    if (tenantId == null || chargeId == null) {
      return null;
    }
    return new PixProviderIndex(providerChargeId, tenantId, chargeId, createdAt);
  }

  private Timestamp toTimestamp(Instant instant) {
    if (instant == null) {
      return null;
    }
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
  }

  private Instant toInstant(Timestamp timestamp) {
    if (timestamp == null) {
      return Instant.EPOCH;
    }
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }
}
