package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.idempotency.IdempotencyTransaction;
import com.apipratudo.billingsaas.model.PixProviderIndex;
import java.util.Optional;

public interface PixProviderIndexStore {

  PixProviderIndex save(PixProviderIndex index);

  default PixProviderIndex save(PixProviderIndex index, IdempotencyTransaction transaction) {
    return save(index);
  }

  Optional<PixProviderIndex> findByProviderChargeId(String providerChargeId);
}
