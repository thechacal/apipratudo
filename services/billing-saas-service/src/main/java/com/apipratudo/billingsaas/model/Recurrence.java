package com.apipratudo.billingsaas.model;

public record Recurrence(
    RecurrenceFrequency frequency,
    int interval,
    Integer dayOfMonth
) {
}
