package com.apipratudo.gateway.webhook;

public class IdempotencyConflictException extends RuntimeException {
  private final String key;

  public IdempotencyConflictException(String key) {
    super("Idempotency-Key reuse with different payload: " + key);
    this.key = key;
  }

  public String getKey() {
    return key;
  }
}
