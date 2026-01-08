package com.apipratudo.gateway.idempotency;

@FunctionalInterface
public interface IdempotencyOperation {

  IdempotencyResponse execute(IdempotencyTransaction transaction);
}
