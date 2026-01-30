package com.apipratudo.scheduling.client;

import java.util.Map;

public record BillingCustomerCreateRequest(
    String name,
    String document,
    String email,
    String phone,
    String externalId,
    Map<String, String> metadata
) {
}
