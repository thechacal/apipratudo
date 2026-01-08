package com.apipratudo.gateway.idempotency;

public interface IdempotencyStore {

  IdempotencyResult execute(IdempotencyRequest request, IdempotencyOperation operation);
}
