package com.apipratudo.lotomania.error;

import java.util.List;

public record ApiErrorResponse(
    String error,
    String message,
    List<String> details
) {
}
