package com.apipratudo.billing.repository;

import com.apipratudo.billing.config.FirestoreProperties;
import com.apipratudo.billing.model.BillingCharge;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@ConditionalOnBean(Firestore.class)
public class FirestoreBillingChargeRepository implements BillingChargeRepository {

  private final Firestore firestore;
  private final FirestoreProperties properties;

  public FirestoreBillingChargeRepository(Firestore firestore, FirestoreProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public Optional<BillingCharge> findById(String chargeId) {
    try {
      DocumentSnapshot snapshot = firestore.collection(collection())
          .document(chargeId)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromSnapshot(snapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Charge lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch charge", e);
    }
  }

  @Override
  public BillingCharge save(BillingCharge charge) {
    Map<String, Object> data = toDocument(charge);
    ApiFuture<?> future = firestore.collection(collection())
        .document(charge.chargeId())
        .set(data, SetOptions.merge());
    try {
      future.get();
      return charge;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Charge save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save charge", e);
    }
  }

  private String collection() {
    return properties.getCollections().getCharges();
  }

  private Map<String, Object> toDocument(BillingCharge charge) {
    Map<String, Object> data = new HashMap<>();
    putIfNotNull(data, "chargeId", charge.chargeId());
    putIfNotNull(data, "referenceId", charge.referenceId());
    putIfNotNull(data, "apiKeyHash", charge.apiKeyHash());
    putIfNotNull(data, "apiKeyPrefix", charge.apiKeyPrefix());
    putIfNotNull(data, "plan", charge.plan());
    putIfNotNull(data, "amountCents", charge.amountCents());
    putIfNotNull(data, "description", charge.description());
    putIfNotNull(data, "statusCharge", charge.statusCharge());
    putIfNotNull(data, "statusTop", charge.statusTop());
    putIfNotNull(data, "paid", charge.paid());
    putIfNotNull(data, "pixCopyPaste", charge.pixCopyPaste());
    putIfNotNull(data, "qrCodeBase64", charge.qrCodeBase64());
    putIfNotNull(data, "createdAt", toTimestamp(charge.createdAt()));
    putIfNotNull(data, "updatedAt", toTimestamp(charge.updatedAt()));
    putIfNotNull(data, "expiresAt", toTimestamp(charge.expiresAt()));
    putIfNotNull(data, "paidAt", toTimestamp(charge.paidAt()));
    putIfNotNull(data, "premiumActivated", charge.premiumActivated());
    return data;
  }

  private void putIfNotNull(Map<String, Object> data, String key, Object value) {
    if (value != null) {
      data.put(key, value);
    }
  }

  private BillingCharge fromSnapshot(DocumentSnapshot snapshot) {
    String chargeId = snapshot.getString("chargeId");
    if (!StringUtils.hasText(chargeId)) {
      chargeId = snapshot.getId();
    }
    return new BillingCharge(
        chargeId,
        snapshot.getString("referenceId"),
        snapshot.getString("apiKeyHash"),
        snapshot.getString("apiKeyPrefix"),
        snapshot.getString("plan"),
        toInteger(snapshot.get("amountCents")),
        snapshot.getString("description"),
        snapshot.getString("statusCharge"),
        snapshot.getString("statusTop"),
        snapshot.getBoolean("paid"),
        toInstant(snapshot.getTimestamp("createdAt")),
        toInstant(snapshot.getTimestamp("updatedAt")),
        toInstant(snapshot.getTimestamp("expiresAt")),
        snapshot.getString("pixCopyPaste"),
        snapshot.getString("qrCodeBase64"),
        toInstant(snapshot.getTimestamp("paidAt")),
        snapshot.getBoolean("premiumActivated")
    );
  }

  private Integer toInteger(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return null;
  }

  private Instant toInstant(Timestamp timestamp) {
    if (timestamp == null) {
      return null;
    }
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  private Timestamp toTimestamp(Instant instant) {
    if (instant == null) {
      return null;
    }
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
  }
}
