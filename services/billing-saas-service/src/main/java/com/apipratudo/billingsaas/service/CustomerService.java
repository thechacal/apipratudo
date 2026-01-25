package com.apipratudo.billingsaas.service;

import com.apipratudo.billingsaas.dto.CustomerCreateRequest;
import com.apipratudo.billingsaas.dto.CustomerResponse;
import com.apipratudo.billingsaas.error.ResourceNotFoundException;
import com.apipratudo.billingsaas.idempotency.IdempotencyTransaction;
import com.apipratudo.billingsaas.model.Customer;
import com.apipratudo.billingsaas.repository.CustomerStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

  private final CustomerStore customerStore;
  private final Clock clock;

  public CustomerService(CustomerStore customerStore, Clock clock) {
    this.customerStore = customerStore;
    this.clock = clock;
  }

  public Customer create(String tenantId, CustomerCreateRequest request, IdempotencyTransaction transaction) {
    Instant now = Instant.now(clock);
    Customer customer = new Customer(
        IdGenerator.customerId(),
        request.getName(),
        request.getDocument(),
        request.getEmail(),
        request.getPhone(),
        request.getExternalId(),
        normalizeMetadata(request.getMetadata()),
        now,
        now
    );
    return customerStore.save(tenantId, customer, transaction);
  }

  public Customer get(String tenantId, String id) {
    Optional<Customer> existing = customerStore.findById(tenantId, id);
    return existing.orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
  }

  public CustomerResponse toResponse(Customer customer) {
    CustomerResponse response = new CustomerResponse();
    response.setId(customer.id());
    response.setName(customer.name());
    response.setDocument(customer.document());
    response.setEmail(customer.email());
    response.setPhone(customer.phone());
    response.setExternalId(customer.externalId());
    response.setMetadata(customer.metadata());
    response.setCreatedAt(customer.createdAt());
    response.setUpdatedAt(customer.updatedAt());
    return response;
  }

  private Map<String, String> normalizeMetadata(Map<String, String> metadata) {
    if (metadata == null) {
      return Map.of();
    }
    return metadata;
  }
}
