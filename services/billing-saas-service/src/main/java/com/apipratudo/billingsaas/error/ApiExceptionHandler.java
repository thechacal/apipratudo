package com.apipratudo.billingsaas.error;

import com.apipratudo.billingsaas.logging.TraceIdUtils;
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

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException ex, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, ex);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException ex, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, ex);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException ex, HttpServletRequest request) {
    return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request, ex);
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ErrorResponse> idempotencyConflict(IdempotencyConflictException ex,
                                                          HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage(), request, ex);
  }

  @ExceptionHandler(ConfigurationException.class)
  public ResponseEntity<ErrorResponse> configuration(ConfigurationException ex, HttpServletRequest request) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIG_ERROR", ex.getMessage(), request, ex);
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
    log.warn("Request failed status={} error={} traceId={} message={}", status.value(), code, traceId, ex.getMessage());
    ErrorResponse body = new ErrorResponse(code, message, Collections.emptyList(), traceId);
    return ResponseEntity.status(status).body(body);
  }
}
