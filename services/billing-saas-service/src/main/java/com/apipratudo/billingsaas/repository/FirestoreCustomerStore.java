package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.config.FirestoreProperties;
import com.apipratudo.billingsaas.idempotency.IdempotencyTransaction;
import com.apipratudo.billingsaas.model.Customer;
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
public class FirestoreCustomerStore implements CustomerStore {

  private static final String TENANTS_COLLECTION = "tenants";

  private final Firestore firestore;
  private final FirestoreProperties properties;

  public FirestoreCustomerStore(Firestore firestore, FirestoreProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public Customer save(String tenantId, Customer customer) {
    Map<String, Object> data = toDocument(customer, tenantId);
    try {
      firestore.collection(collectionPath(tenantId))
          .document(customer.id())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Customer save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save customer", e);
    }
    return customer;
  }

  @Override
  public Customer save(String tenantId, Customer customer, IdempotencyTransaction transaction) {
    Map<String, Object> data = toDocument(customer, tenantId);
    if (transaction.isNoop()) {
      return save(tenantId, customer);
    }
    transaction.set(collectionPath(tenantId), customer.id(), data);
    return customer;
  }

  @Override
  public Optional<Customer> findById(String tenantId, String id) {
    try {
      DocumentSnapshot snapshot = firestore.collection(collectionPath(tenantId))
          .document(id)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromSnapshot(snapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Customer lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch customer", e);
    }
  }

  private String collectionPath(String tenantId) {
    return TENANTS_COLLECTION + "/" + tenantId + "/" + properties.getCollections().getCustomers();
  }

  private Map<String, Object> toDocument(Customer customer, String tenantId) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", customer.id());
    data.put("tenantId", tenantId);
    data.put("name", customer.name());
    data.put("document", customer.document());
    data.put("email", customer.email());
    data.put("phone", customer.phone());
    if (customer.externalId() != null) {
      data.put("externalId", customer.externalId());
    }
    if (customer.metadata() != null && !customer.metadata().isEmpty()) {
      data.put("metadata", customer.metadata());
    }
    data.put("createdAt", toTimestamp(customer.createdAt()));
    data.put("updatedAt", toTimestamp(customer.updatedAt()));
    return data;
  }

  private Customer fromSnapshot(DocumentSnapshot snapshot) {
    String id = snapshot.getString("id");
    if (id == null) {
      id = snapshot.getId();
    }
    String name = snapshot.getString("name");
    String document = snapshot.getString("document");
    String email = snapshot.getString("email");
    String phone = snapshot.getString("phone");
    String externalId = snapshot.getString("externalId");
    Map<String, String> metadata = readStringMap(snapshot.get("metadata"));
    Instant createdAt = toInstant(snapshot.getTimestamp("createdAt"));
    Instant updatedAt = toInstant(snapshot.getTimestamp("updatedAt"));

    if (name == null || document == null || email == null || phone == null) {
      return null;
    }

    return new Customer(
        id,
        name,
        document,
        email,
        phone,
        externalId,
        metadata,
        createdAt,
        updatedAt
    );
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
