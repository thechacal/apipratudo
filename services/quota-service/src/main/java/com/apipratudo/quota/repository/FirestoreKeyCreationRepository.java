package com.apipratudo.quota.repository;

import com.apipratudo.quota.config.FirestoreProperties;
import com.apipratudo.quota.service.HashingUtils;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(Firestore.class)
public class FirestoreKeyCreationRepository implements KeyCreationRepository {

  private final Firestore firestore;
  private final FirestoreProperties properties;

  public FirestoreKeyCreationRepository(Firestore firestore, FirestoreProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public KeyCreationResult tryReserve(String email, String org, LocalDate day, int maxPerEmail, int maxPerOrg) {
    String dayKey = day.toString();
    String emailKey = ("email:" + email.toLowerCase() + ":" + dayKey);
    String orgKey = ("org:" + org.toLowerCase() + ":" + dayKey);
    DocumentReference emailRef = docRef(emailKey);
    DocumentReference orgRef = docRef(orgKey);
    Instant now = Instant.now();

    ApiFuture<KeyCreationResult> future = firestore.runTransaction(transaction -> {
      DocumentSnapshot emailSnap = transaction.get(emailRef).get();
      DocumentSnapshot orgSnap = transaction.get(orgRef).get();

      int emailCount = (int) getLong(emailSnap, "count");
      int orgCount = (int) getLong(orgSnap, "count");

      if (emailCount + 1 > maxPerEmail || orgCount + 1 > maxPerOrg) {
        return new KeyCreationResult(false, emailCount, orgCount);
      }

      Map<String, Object> emailData = new HashMap<>();
      emailData.put("key", emailKey);
      emailData.put("type", "email");
      emailData.put("email", email.toLowerCase());
      emailData.put("day", dayKey);
      emailData.put("count", emailCount + 1);
      emailData.put("updatedAt", toTimestamp(now));
      if (!emailSnap.exists()) {
        emailData.put("createdAt", toTimestamp(now));
      }

      Map<String, Object> orgData = new HashMap<>();
      orgData.put("key", orgKey);
      orgData.put("type", "org");
      orgData.put("org", org.toLowerCase());
      orgData.put("day", dayKey);
      orgData.put("count", orgCount + 1);
      orgData.put("updatedAt", toTimestamp(now));
      if (!orgSnap.exists()) {
        orgData.put("createdAt", toTimestamp(now));
      }

      transaction.set(emailRef, emailData, SetOptions.merge());
      transaction.set(orgRef, orgData, SetOptions.merge());
      return new KeyCreationResult(true, emailCount + 1, orgCount + 1);
    });

    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Key creation interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to reserve key creation", e);
    }
  }

  private DocumentReference docRef(String key) {
    String id = HashingUtils.sha256Hex(key);
    return firestore.collection(properties.getCollections().getKeyCreationLimits()).document(id);
  }

  private long getLong(DocumentSnapshot snapshot, String field) {
    if (snapshot == null || !snapshot.exists()) {
      return 0;
    }
    Long value = snapshot.getLong(field);
    return value == null ? 0 : value;
  }

  private Timestamp toTimestamp(Instant instant) {
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
  }
}
