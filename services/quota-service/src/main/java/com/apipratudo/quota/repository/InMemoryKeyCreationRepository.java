package com.apipratudo.quota.repository;

import com.google.cloud.firestore.Firestore;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(Firestore.class)
public class InMemoryKeyCreationRepository implements KeyCreationRepository {

  private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();

  @Override
  public synchronized KeyCreationResult tryReserve(String email, String org, LocalDate day, int maxPerEmail,
      int maxPerOrg) {
    String dayKey = day.toString();
    String emailKey = "email:" + email.toLowerCase() + ":" + dayKey;
    String orgKey = "org:" + org.toLowerCase() + ":" + dayKey;

    int emailCount = counters.getOrDefault(emailKey, new Counter(0)).count();
    int orgCount = counters.getOrDefault(orgKey, new Counter(0)).count();

    if (emailCount + 1 > maxPerEmail || orgCount + 1 > maxPerOrg) {
      return new KeyCreationResult(false, emailCount, orgCount);
    }

    counters.put(emailKey, new Counter(emailCount + 1));
    counters.put(orgKey, new Counter(orgCount + 1));
    return new KeyCreationResult(true, emailCount + 1, orgCount + 1);
  }

  private record Counter(int count) {
  }
}
