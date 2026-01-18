package com.apipratudo.quota.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.List;
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

@RestControllerAdvice(basePackages = "com.apipratudo.quota")
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<String> details = ex.getBindingResult().getFieldErrors().stream()
        .map(ApiExceptionHandler::formatFieldError)
        .collect(Collectors.toList());
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation error", details, ex);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
    List<String> details = ex.getConstraintViolations().stream()
        .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
        .collect(Collectors.toList());
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation error", details, ex);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleBadJson(HttpMessageNotReadableException ex) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Malformed JSON request", Collections.emptyList(), ex);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), Collections.emptyList(), ex);
  }

  @ExceptionHandler(KeyCreationLimitException.class)
  public ResponseEntity<ErrorResponse> handleKeyLimit(KeyCreationLimitException ex) {
    return error(HttpStatus.TOO_MANY_REQUESTS, "KEY_CREATION_LIMIT", ex.getMessage(), Collections.emptyList(), ex);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), Collections.emptyList(), ex);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", Collections.emptyList(), ex);
  }

  private static ResponseEntity<ErrorResponse> error(
      HttpStatus status,
      String error,
      String message,
      List<String> details,
      Exception ex
  ) {
    List<String> safeDetails = Optional.ofNullable(details).orElse(Collections.emptyList());
    ErrorResponse response = new ErrorResponse(error, message, safeDetails);

    if (status.is5xxServerError()) {
      log.error("Request failed status={} error={} message={}", status.value(), error, message, ex);
    } else {
      log.warn("Request failed status={} error={} message={}", status.value(), error, message, ex);
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
