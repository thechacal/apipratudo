package com.apipratudo.reconciliation.error;

import com.apipratudo.reconciliation.logging.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
    log.warn("Request failed status={} error={} traceId={} message={}", ex.getStatus(), ex.getError(),
        TraceIdUtils.resolveTraceId(request), ex.getMessage());
    return ResponseEntity.status(ex.getStatus())
        .body(new ErrorResponse(ex.getError(), ex.getMessage(), Collections.emptyList(),
            TraceIdUtils.resolveTraceId(request)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("VALIDATION_ERROR", "Invalid request", Collections.emptyList(),
            TraceIdUtils.resolveTraceId(request)));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
    log.error("Unhandled error traceId={} message={}", TraceIdUtils.resolveTraceId(request), ex.getMessage(), ex);
    return ResponseEntity.status(500)
        .body(new ErrorResponse("INTERNAL_ERROR", "Unexpected error", Collections.emptyList(),
            TraceIdUtils.resolveTraceId(request)));
  }
}
