package com.apipratudo.scheduling.error;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends ApiException {

  public IdempotencyConflictException(String idempotencyKey) {
    super(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
        "Idempotency-Key reuse with different payload: " + idempotencyKey);
  }
}
