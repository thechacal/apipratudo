package com.apipratudo.billingsaas.controller;

import com.apipratudo.billingsaas.dto.CustomerCreateRequest;
import com.apipratudo.billingsaas.dto.CustomerResponse;
import com.apipratudo.billingsaas.idempotency.IdempotencyResponse;
import com.apipratudo.billingsaas.idempotency.IdempotencyResult;
import com.apipratudo.billingsaas.idempotency.IdempotencyService;
import com.apipratudo.billingsaas.model.Customer;
import com.apipratudo.billingsaas.service.CustomerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/clientes")
@Validated
@Tag(name = "clientes")
@SecurityRequirement(name = "ServiceToken")
public class InternalCustomersController {

  private final CustomerService customerService;
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;

  public InternalCustomersController(
      CustomerService customerService,
      IdempotencyService idempotencyService,
      ObjectMapper objectMapper
  ) {
    this.customerService = customerService;
    this.idempotencyService = idempotencyService;
    this.objectMapper = objectMapper;
  }

  @PostMapping
  public ResponseEntity<String> create(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CustomerCreateRequest request,
      HttpServletRequest httpRequest
  ) {
    IdempotencyResult result = idempotencyService.execute(
        tenantId,
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        idempotencyKey,
        request,
        transaction -> {
          Customer customer = customerService.create(tenantId, request, transaction);
          CustomerResponse response = customerService.toResponse(customer);
          return new IdempotencyResponse(
              HttpStatus.CREATED.value(),
              toJson(response),
              null
          );
        }
    );

    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.responseBodyJson());
  }

  @GetMapping("/{id}")
  public ResponseEntity<CustomerResponse> get(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @PathVariable String id
  ) {
    Customer customer = customerService.get(tenantId, id);
    return ResponseEntity.ok(customerService.toResponse(customer));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize response", e);
    }
  }
}
