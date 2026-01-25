package com.apipratudo.billingsaas.idempotency;

public interface IdempotencyStore {
  IdempotencyResult execute(IdempotencyRequest request, IdempotencyOperation operation);
}
