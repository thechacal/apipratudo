package com.apipratudo.gateway.idempotency;

import java.util.Map;

public interface IdempotencyTransaction {

  void set(String collection, String documentId, Map<String, Object> data);
}
