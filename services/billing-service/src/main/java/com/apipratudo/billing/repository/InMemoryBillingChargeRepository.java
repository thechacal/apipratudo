package com.apipratudo.billing.repository;

import com.apipratudo.billing.model.BillingCharge;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(Firestore.class)
public class InMemoryBillingChargeRepository implements BillingChargeRepository {

  private final ConcurrentMap<String, BillingCharge> storage = new ConcurrentHashMap<>();

  @Override
  public Optional<BillingCharge> findById(String chargeId) {
    return Optional.ofNullable(storage.get(chargeId));
  }

  @Override
  public BillingCharge save(BillingCharge charge) {
    storage.put(charge.chargeId(), charge);
    return charge;
  }
}
