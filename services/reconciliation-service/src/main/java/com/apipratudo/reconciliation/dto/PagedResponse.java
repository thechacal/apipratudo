package com.apipratudo.reconciliation.dto;

import java.util.List;

public record PagedResponse<T>(
    List<T> items,
    int page,
    int size,
    long total
) {
}
