package com.apipratudo.scheduling.idempotency;

public interface IdempotencyStore {
  IdempotencyResult execute(IdempotencyRequest request, IdempotencyOperation operation);
}
