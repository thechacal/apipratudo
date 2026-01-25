package com.apipratudo.billingsaas.idempotency;

import java.util.Map;

public interface IdempotencyTransaction {
  void set(String collection, String documentId, Map<String, Object> data);

  default boolean isNoop() {
    return false;
  }
}
