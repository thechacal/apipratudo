package com.apipratudo.billing.repository;

import com.apipratudo.billing.model.BillingCharge;
import java.util.Optional;

public interface BillingChargeRepository {

  Optional<BillingCharge> findById(String chargeId);

  BillingCharge save(BillingCharge charge);
}
