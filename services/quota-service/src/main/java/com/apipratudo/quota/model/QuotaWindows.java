package com.apipratudo.quota.model;

public record QuotaWindows(
    QuotaWindow minute,
    QuotaWindow day
) {
}
