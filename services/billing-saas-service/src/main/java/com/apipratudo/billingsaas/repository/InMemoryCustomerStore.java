package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.model.Customer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(FirestoreCustomerStore.class)
public class InMemoryCustomerStore implements CustomerStore {

  private final ConcurrentMap<String, Map<String, Customer>> store = new ConcurrentHashMap<>();

  @Override
  public Customer save(String tenantId, Customer customer) {
    store.computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>())
        .put(customer.id(), customer);
    return customer;
  }

  @Override
  public Optional<Customer> findById(String tenantId, String id) {
    Map<String, Customer> tenantMap = store.get(tenantId);
    if (tenantMap == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(tenantMap.get(id));
  }
}
