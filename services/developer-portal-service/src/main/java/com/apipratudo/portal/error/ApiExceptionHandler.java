package com.apipratudo.portal.error;

import jakarta.validation.ConstraintViolationException;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.apipratudo.portal")
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    String detail = ex.getBindingResult().getFieldErrors().stream()
        .map(ApiExceptionHandler::formatFieldError)
        .collect(Collectors.joining(", "));
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, ex);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
    String detail = ex.getConstraintViolations().stream()
        .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
        .collect(Collectors.joining(", "));
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, ex);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleBadJson(HttpMessageNotReadableException ex) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Malformed JSON request", ex);
  }

  @ExceptionHandler(RateLimitException.class)
  public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitException ex) {
    return error(HttpStatus.TOO_MANY_REQUESTS, ex.getError(), ex.getMessage(), ex);
  }

  @ExceptionHandler(QuotaServiceException.class)
  public ResponseEntity<ErrorResponse> handleQuotaService(QuotaServiceException ex) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
    if (status == null) {
      status = HttpStatus.BAD_GATEWAY;
    }
    return error(status, ex.getError(), ex.getMessage(), ex);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), ex);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", ex);
  }

  private ResponseEntity<ErrorResponse> error(HttpStatus status, String error, String message, Exception ex) {
    ErrorResponse response = new ErrorResponse(error, message);
    if (status.is5xxServerError()) {
      log.error("Request failed status={} error={}", status.value(), error, ex);
    } else {
      log.warn("Request failed status={} error={}", status.value(), error, ex);
    }
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(response);
  }

  private static String formatFieldError(FieldError error) {
    String message = Optional.ofNullable(error.getDefaultMessage()).orElse("is invalid");
    return error.getField() + " " + message;
  }
}
