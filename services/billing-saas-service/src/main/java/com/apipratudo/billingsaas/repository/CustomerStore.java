package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.idempotency.IdempotencyTransaction;
import com.apipratudo.billingsaas.model.Customer;
import java.util.Optional;

public interface CustomerStore {

  Customer save(String tenantId, Customer customer);

  default Customer save(String tenantId, Customer customer, IdempotencyTransaction transaction) {
    return save(tenantId, customer);
  }

  Optional<Customer> findById(String tenantId, String id);
}
