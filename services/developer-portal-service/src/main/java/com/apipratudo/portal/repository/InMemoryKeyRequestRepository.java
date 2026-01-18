package com.apipratudo.portal.repository;

import com.google.cloud.firestore.Firestore;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(Firestore.class)
public class InMemoryKeyRequestRepository implements KeyRequestRepository {

  private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();

  @Override
  public synchronized KeyRequestResult tryReserve(String email, String org, LocalDate day, int maxPerEmail,
      int maxPerOrg) {
    String dayKey = day.toString();
    String emailKey = "email:" + email.toLowerCase() + ":" + dayKey;
    String orgKey = "org:" + org.toLowerCase() + ":" + dayKey;

    int emailCount = counters.getOrDefault(emailKey, new Counter(0)).count();
    int orgCount = counters.getOrDefault(orgKey, new Counter(0)).count();

    if (emailCount + 1 > maxPerEmail || orgCount + 1 > maxPerOrg) {
      return new KeyRequestResult(false, emailCount, orgCount);
    }

    counters.put(emailKey, new Counter(emailCount + 1));
    counters.put(orgKey, new Counter(orgCount + 1));
    return new KeyRequestResult(true, emailCount + 1, orgCount + 1);
  }

  private record Counter(int count) {
  }
}
