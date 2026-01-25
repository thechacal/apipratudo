package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.idempotency.IdempotencyTransaction;
import com.apipratudo.billingsaas.model.Charge;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChargeStore {

  Charge save(String tenantId, Charge charge);

  default Charge save(String tenantId, Charge charge, IdempotencyTransaction transaction) {
    return save(tenantId, charge);
  }

  Optional<Charge> findById(String tenantId, String id);

  Optional<Charge> findByProviderChargeId(String providerChargeId);

  List<Charge> findByCreatedAtBetween(String tenantId, Instant start, Instant end);
}
