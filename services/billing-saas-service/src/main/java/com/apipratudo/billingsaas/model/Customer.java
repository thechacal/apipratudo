package com.apipratudo.billingsaas.model;

import java.time.Instant;
import java.util.Map;

public record Customer(
    String id,
    String name,
    String document,
    String email,
    String phone,
    String externalId,
    Map<String, String> metadata,
    Instant createdAt,
    Instant updatedAt
) {
}
