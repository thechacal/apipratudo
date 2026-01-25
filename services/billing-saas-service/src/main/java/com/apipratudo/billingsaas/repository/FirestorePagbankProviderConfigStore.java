package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.model.EncryptedValue;
import com.apipratudo.billingsaas.model.PagbankEnvironment;
import com.apipratudo.billingsaas.model.PagbankProviderConfig;
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
public class FirestorePagbankProviderConfigStore implements PagbankProviderConfigStore {

  private static final String TENANTS_COLLECTION = "tenants";
  private static final String PROVIDERS_COLLECTION = "providers";
  private static final String PAGBANK_DOC = "pagbank";

  private final Firestore firestore;

  public FirestorePagbankProviderConfigStore(Firestore firestore) {
    this.firestore = firestore;
  }

  @Override
  public PagbankProviderConfig save(String tenantId, PagbankProviderConfig config) {
    try {
      firestore.collection(collectionPath(tenantId))
          .document(PAGBANK_DOC)
          .set(toDocument(config))
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("PagBank config save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save PagBank config", e);
    }
    return config;
  }

  @Override
  public Optional<PagbankProviderConfig> find(String tenantId) {
    if (tenantId == null) {
      return Optional.empty();
    }
    try {
      DocumentSnapshot snapshot = firestore.collection(collectionPath(tenantId))
          .document(PAGBANK_DOC)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromSnapshot(tenantId, snapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("PagBank config lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch PagBank config", e);
    }
  }

  @Override
  public void delete(String tenantId) {
    if (tenantId == null) {
      return;
    }
    try {
      firestore.collection(collectionPath(tenantId))
          .document(PAGBANK_DOC)
          .delete()
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("PagBank config delete interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to delete PagBank config", e);
    }
  }

  private String collectionPath(String tenantId) {
    return TENANTS_COLLECTION + "/" + tenantId + "/" + PROVIDERS_COLLECTION;
  }

  private Map<String, Object> toDocument(PagbankProviderConfig config) {
    Map<String, Object> data = new HashMap<>();
    data.put("tenantId", config.tenantId());
    data.put("enabled", config.enabled());
    if (config.environment() != null) {
      data.put("environment", config.environment().name());
    }
    if (config.token() != null) {
      data.put("token", encryptedToMap(config.token()));
    }
    if (config.webhookToken() != null) {
      data.put("webhookToken", encryptedToMap(config.webhookToken()));
    }
    if (config.fingerprint() != null) {
      data.put("fingerprint", config.fingerprint());
    }
    data.put("createdAt", toTimestamp(config.createdAt()));
    data.put("updatedAt", toTimestamp(config.updatedAt()));
    if (config.lastVerifiedAt() != null) {
      data.put("lastVerifiedAt", toTimestamp(config.lastVerifiedAt()));
    }
    return data;
  }

  private PagbankProviderConfig fromSnapshot(String tenantId, DocumentSnapshot snapshot) {
    Boolean enabled = snapshot.getBoolean("enabled");
    String environment = snapshot.getString("environment");
    EncryptedValue token = mapToEncrypted(snapshot.get("token"));
    EncryptedValue webhookToken = mapToEncrypted(snapshot.get("webhookToken"));
    String fingerprint = snapshot.getString("fingerprint");
    Instant createdAt = toInstant(snapshot.getTimestamp("createdAt"));
    Instant updatedAt = toInstant(snapshot.getTimestamp("updatedAt"));
    Instant lastVerifiedAt = toInstant(snapshot.getTimestamp("lastVerifiedAt"));

    if (token == null) {
      return null;
    }

    return new PagbankProviderConfig(
        tenantId,
        enabled != null && enabled,
        PagbankEnvironment.fromString(environment),
        token,
        webhookToken,
        fingerprint,
        createdAt,
        updatedAt,
        lastVerifiedAt
    );
  }

  private Map<String, Object> encryptedToMap(EncryptedValue value) {
    Map<String, Object> data = new HashMap<>();
    data.put("cipherTextBase64", value.cipherTextBase64());
    data.put("ivBase64", value.ivBase64());
    data.put("version", value.version());
    return data;
  }

  private EncryptedValue mapToEncrypted(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) {
      return null;
    }
    String cipherText = stringValue(map.get("cipherTextBase64"));
    String iv = stringValue(map.get("ivBase64"));
    Integer version = intValue(map.get("version"));
    if (cipherText == null || iv == null) {
      return null;
    }
    return new EncryptedValue(cipherText, iv, version == null ? 1 : version);
  }

  private String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    return String.valueOf(value);
  }

  private Integer intValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (Exception ex) {
      return null;
    }
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
}
