package com.apipratudo.portal.repository;

import com.apipratudo.portal.config.FirestoreProperties;
import com.apipratudo.portal.service.HashingUtils;
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
public class FirestoreKeyRequestRepository implements KeyRequestRepository {

  private final Firestore firestore;
  private final FirestoreProperties properties;

  public FirestoreKeyRequestRepository(Firestore firestore, FirestoreProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public KeyRequestResult tryReserve(String email, String org, LocalDate day, int maxPerEmail, int maxPerOrg) {
    String dayKey = day.toString();
    String emailKey = "email:" + email.toLowerCase() + ":" + dayKey;
    String orgKey = "org:" + org.toLowerCase() + ":" + dayKey;
    DocumentReference emailRef = docRef(emailKey);
    DocumentReference orgRef = docRef(orgKey);
    Instant now = Instant.now();

    ApiFuture<KeyRequestResult> future = firestore.runTransaction(transaction -> {
      DocumentSnapshot emailSnap = transaction.get(emailRef).get();
      DocumentSnapshot orgSnap = transaction.get(orgRef).get();

      int emailCount = (int) getLong(emailSnap, "count");
      int orgCount = (int) getLong(orgSnap, "count");

      if (emailCount + 1 > maxPerEmail || orgCount + 1 > maxPerOrg) {
        return new KeyRequestResult(false, emailCount, orgCount);
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
      return new KeyRequestResult(true, emailCount + 1, orgCount + 1);
    });

    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Key request interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to reserve key request", e);
    }
  }

  private DocumentReference docRef(String key) {
    String id = HashingUtils.sha256Hex(key);
    return firestore.collection(properties.getCollections().getKeyRequests()).document(id);
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
