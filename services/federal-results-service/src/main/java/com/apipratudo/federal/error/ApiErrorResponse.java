package com.apipratudo.federal.error;

import java.util.List;

public record ApiErrorResponse(
    String error,
    String message,
    List<String> details
) {
}
