package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.config.FirestoreProperties;
import com.apipratudo.billingsaas.idempotency.IdempotencyTransaction;
import com.apipratudo.billingsaas.model.Charge;
import com.apipratudo.billingsaas.model.ChargeStatus;
import com.apipratudo.billingsaas.model.PixData;
import com.apipratudo.billingsaas.model.Recurrence;
import com.apipratudo.billingsaas.model.RecurrenceFrequency;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(Firestore.class)
public class FirestoreChargeStore implements ChargeStore {

  private static final String TENANTS_COLLECTION = "tenants";

  private final Firestore firestore;
  private final FirestoreProperties properties;

  public FirestoreChargeStore(Firestore firestore, FirestoreProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public Charge save(String tenantId, Charge charge) {
    Map<String, Object> data = toDocument(charge, tenantId);
    try {
      firestore.collection(collectionPath(tenantId))
          .document(charge.id())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Charge save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save charge", e);
    }
    return charge;
  }

  @Override
  public Charge save(String tenantId, Charge charge, IdempotencyTransaction transaction) {
    Map<String, Object> data = toDocument(charge, tenantId);
    if (transaction.isNoop()) {
      return save(tenantId, charge);
    }
    transaction.set(collectionPath(tenantId), charge.id(), data);
    return charge;
  }

  @Override
  public Optional<Charge> findById(String tenantId, String id) {
    try {
      DocumentSnapshot snapshot = firestore.collection(collectionPath(tenantId))
          .document(id)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromSnapshot(snapshot, tenantId));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Charge lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch charge", e);
    }
  }

  @Override
  public Optional<Charge> findByProviderChargeId(String providerChargeId) {
    if (providerChargeId == null) {
      return Optional.empty();
    }
    try {
      QuerySnapshot snapshot = firestore.collectionGroup(properties.getCollections().getCharges())
          .whereEqualTo("providerChargeId", providerChargeId)
          .limit(1)
          .get()
          .get();
      if (snapshot.isEmpty()) {
        return Optional.empty();
      }
      DocumentSnapshot doc = snapshot.getDocuments().get(0);
      String tenantId = doc.getString("tenantId");
      if (tenantId == null && doc.getReference().getParent() != null) {
        if (doc.getReference().getParent().getParent() != null) {
          tenantId = doc.getReference().getParent().getParent().getId();
        }
      }
      return Optional.ofNullable(fromSnapshot(doc, tenantId));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Charge lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch charge", e);
    }
  }

  @Override
  public List<Charge> findByCreatedAtBetween(String tenantId, Instant start, Instant end) {
    try {
      QuerySnapshot snapshot = firestore.collection(collectionPath(tenantId))
          .whereGreaterThanOrEqualTo("createdAt", Timestamp.ofTimeSecondsAndNanos(start.getEpochSecond(), start.getNano()))
          .whereLessThanOrEqualTo("createdAt", Timestamp.ofTimeSecondsAndNanos(end.getEpochSecond(), end.getNano()))
          .orderBy("createdAt", Query.Direction.ASCENDING)
          .get()
          .get();
      List<Charge> charges = new ArrayList<>();
      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        Charge charge = fromSnapshot(doc, tenantId);
        if (charge != null) {
          charges.add(charge);
        }
      }
      return charges;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Charge list interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to list charges", e);
    }
  }

  private String collectionPath(String tenantId) {
    return TENANTS_COLLECTION + "/" + tenantId + "/" + properties.getCollections().getCharges();
  }

  private Map<String, Object> toDocument(Charge charge, String tenantId) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", charge.id());
    data.put("tenantId", tenantId);
    data.put("customerId", charge.customerId());
    data.put("amountCents", charge.amountCents());
    data.put("currency", charge.currency());
    if (charge.description() != null) {
      data.put("description", charge.description());
    }
    if (charge.dueDate() != null) {
      data.put("dueDate", charge.dueDate().toString());
    }
    if (charge.recurrence() != null) {
      data.put("recurrence", recurrenceToMap(charge.recurrence()));
    }
    if (charge.metadata() != null && !charge.metadata().isEmpty()) {
      data.put("metadata", charge.metadata());
    }
    if (charge.status() != null) {
      data.put("status", charge.status().name());
    }
    data.put("createdAt", toTimestamp(charge.createdAt()));
    data.put("updatedAt", toTimestamp(charge.updatedAt()));
    if (charge.paidAt() != null) {
      data.put("paidAt", toTimestamp(charge.paidAt()));
    }
    if (charge.pix() != null) {
      data.put("pix", pixToMap(charge.pix()));
    }
    if (charge.providerChargeId() != null) {
      data.put("providerChargeId", charge.providerChargeId());
    }
    return data;
  }

  private Charge fromSnapshot(DocumentSnapshot snapshot, String tenantId) {
    String id = snapshot.getString("id");
    if (id == null) {
      id = snapshot.getId();
    }
    String customerId = snapshot.getString("customerId");
    Long amountCents = snapshot.getLong("amountCents");
    String currency = snapshot.getString("currency");
    String description = snapshot.getString("description");
    String dueDateRaw = snapshot.getString("dueDate");
    LocalDate dueDate = dueDateRaw == null ? null : LocalDate.parse(dueDateRaw);
    Recurrence recurrence = mapToRecurrence(snapshot.get("recurrence"));
    Map<String, String> metadata = readStringMap(snapshot.get("metadata"));
    String statusRaw = snapshot.getString("status");
    ChargeStatus status = statusRaw == null ? ChargeStatus.CREATED : ChargeStatus.valueOf(statusRaw);
    Instant createdAt = toInstant(snapshot.getTimestamp("createdAt"));
    Instant updatedAt = toInstant(snapshot.getTimestamp("updatedAt"));
    Instant paidAt = toInstant(snapshot.getTimestamp("paidAt"));
    PixData pixData = mapToPix(snapshot.get("pix"));
    String providerChargeId = snapshot.getString("providerChargeId");

    if (customerId == null || amountCents == null) {
      return null;
    }

    return new Charge(
        id,
        customerId,
        amountCents,
        currency == null ? "BRL" : currency,
        description,
        dueDate,
        recurrence,
        metadata,
        status,
        createdAt,
        updatedAt,
        paidAt,
        pixData,
        providerChargeId,
        tenantId
    );
  }

  private Map<String, Object> recurrenceToMap(Recurrence recurrence) {
    Map<String, Object> data = new HashMap<>();
    if (recurrence.frequency() != null) {
      data.put("frequency", recurrence.frequency().name());
    }
    data.put("interval", recurrence.interval());
    if (recurrence.dayOfMonth() != null) {
      data.put("dayOfMonth", recurrence.dayOfMonth());
    }
    return data;
  }

  private Recurrence mapToRecurrence(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) {
      return null;
    }
    Object frequencyRaw = map.get("frequency");
    RecurrenceFrequency frequency = frequencyRaw == null ? null : RecurrenceFrequency.valueOf(frequencyRaw.toString());
    Integer interval = map.get("interval") == null ? null : Integer.parseInt(map.get("interval").toString());
    Integer dayOfMonth = map.get("dayOfMonth") == null ? null : Integer.parseInt(map.get("dayOfMonth").toString());
    if (frequency == null) {
      return null;
    }
    int safeInterval = interval == null || interval < 1 ? 1 : interval;
    return new Recurrence(frequency, safeInterval, dayOfMonth);
  }

  private Map<String, Object> pixToMap(PixData pix) {
    Map<String, Object> data = new HashMap<>();
    data.put("provider", pix.provider());
    data.put("providerChargeId", pix.providerChargeId());
    data.put("txid", pix.txid());
    data.put("pixCopyPaste", pix.pixCopyPaste());
    data.put("qrCodeBase64", pix.qrCodeBase64());
    if (pix.expiresAt() != null) {
      data.put("expiresAt", toTimestamp(pix.expiresAt()));
    }
    return data;
  }

  private PixData mapToPix(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) {
      return null;
    }
    String provider = toStringValue(map.get("provider"));
    String providerChargeId = toStringValue(map.get("providerChargeId"));
    String txid = toStringValue(map.get("txid"));
    String pixCopyPaste = toStringValue(map.get("pixCopyPaste"));
    String qrCodeBase64 = toStringValue(map.get("qrCodeBase64"));
    Instant expiresAt = null;
    Object expiresRaw = map.get("expiresAt");
    if (expiresRaw instanceof Timestamp timestamp) {
      expiresAt = toInstant(timestamp);
    }
    if (provider == null || providerChargeId == null) {
      return null;
    }
    return new PixData(provider, providerChargeId, txid, pixCopyPaste, qrCodeBase64, expiresAt);
  }

  private String toStringValue(Object value) {
    if (value == null) {
      return null;
    }
    return value.toString();
  }

  private Timestamp toTimestamp(Instant instant) {
    if (instant == null) {
      return null;
    }
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
  }

  private Instant toInstant(Timestamp timestamp) {
    if (timestamp == null) {
      return null;
    }
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> readStringMap(Object raw) {
    if (raw instanceof Map<?, ?> map) {
      Map<String, String> data = new HashMap<>();
      map.forEach((key, value) -> {
        if (key != null && value != null) {
          data.put(key.toString(), value.toString());
        }
      });
      return data;
    }
    return Map.of();
  }
}
