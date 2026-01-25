package com.apipratudo.billingsaas.idempotency;

public interface IdempotencyOperation {
  IdempotencyResponse execute(IdempotencyTransaction transaction);
}
