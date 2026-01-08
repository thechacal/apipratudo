package com.apipratudo.gateway.error;

import com.apipratudo.gateway.webhook.IdempotencyConflictException;
import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.apipratudo.gateway")
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<String> details = ex.getBindingResult().getFieldErrors().stream()
        .map(ApiExceptionHandler::formatFieldError)
        .collect(Collectors.toList());
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation error", details);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
    List<String> details = ex.getConstraintViolations().stream()
        .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
        .collect(Collectors.toList());
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation error", details);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String detail = "Invalid value for " + ex.getName();
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation error", List.of(detail));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleBadJson(HttpMessageNotReadableException ex) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Malformed JSON request", Collections.emptyList());
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
    List<String> details = ex.getDetails() == null ? Collections.emptyList() : ex.getDetails();
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), details);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), Collections.emptyList());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
    return error(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), Collections.emptyList());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", Collections.emptyList());
  }

  private static ResponseEntity<ErrorResponse> error(
      HttpStatus status,
      String error,
      String message,
      List<String> details
  ) {
    List<String> safeDetails = Optional.ofNullable(details).orElse(Collections.emptyList());
    ErrorResponse response = new ErrorResponse(error, message, safeDetails);
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(response);
  }

  private static String formatFieldError(FieldError error) {
    String message = Optional.ofNullable(error.getDefaultMessage()).orElse("is invalid");
    return error.getField() + " " + message;
  }
}
