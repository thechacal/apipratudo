package com.apipratudo.gateway.webhook;

public class IdempotencyConflictException extends RuntimeException {
  public IdempotencyConflictException(String key) {
    super("Idempotency-Key reuse with different payload: " + key);
  }
}
