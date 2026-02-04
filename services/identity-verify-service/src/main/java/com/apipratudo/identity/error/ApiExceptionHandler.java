package com.apipratudo.identity.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
    ErrorResponse body = new ErrorResponse(ex.getError(), ex.getMessage(), Collections.emptyList(),
        request.getHeader("X-Request-Id"));
    return ResponseEntity.status(ex.getStatus()).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
    ErrorResponse body = new ErrorResponse("VALIDATION_ERROR", "Invalid request", Collections.emptyList(),
        request.getHeader("X-Request-Id"));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
    log.warn("Unhandled error traceId={} message={}", request.getHeader("X-Request-Id"), ex.getMessage());
    ErrorResponse body = new ErrorResponse("INTERNAL_ERROR", "Unexpected error", Collections.emptyList(),
        request.getHeader("X-Request-Id"));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
