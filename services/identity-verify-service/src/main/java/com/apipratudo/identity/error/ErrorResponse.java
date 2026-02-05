package com.apipratudo.identity.error;

import java.util.List;

public record ErrorResponse(String error, String message, List<String> details, String traceId) {
}
