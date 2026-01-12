package com.apipratudo.quota.model;

public record QuotaStatus(
    QuotaWindowStatus minute,
    QuotaWindowStatus day
) {
}
