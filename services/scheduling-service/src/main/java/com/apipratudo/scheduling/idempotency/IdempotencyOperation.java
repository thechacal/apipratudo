package com.apipratudo.scheduling.idempotency;

@FunctionalInterface
public interface IdempotencyOperation {
  IdempotencyResponse execute(IdempotencyTransaction transaction);
}
