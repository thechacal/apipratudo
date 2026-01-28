package com.apipratudo.scheduling.error;

import com.apipratudo.scheduling.logging.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
    return error(ex.getStatus(), ex.getCode(), ex.getMessage(), request, ex);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException ex, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, ex);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> internal(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", request, ex);
  }

  private ResponseEntity<ErrorResponse> error(
      HttpStatus status,
      String code,
      String message,
      HttpServletRequest request,
      Exception ex
  ) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    log.warn("Request failed status={} error={} traceId={} message={}", status.value(), code, traceId,
        ex.getMessage());
    ErrorResponse body = new ErrorResponse(code, message, Collections.emptyList(), traceId);
    return ResponseEntity.status(status).body(body);
  }
}
