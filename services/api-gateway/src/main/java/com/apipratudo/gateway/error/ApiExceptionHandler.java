package com.apipratudo.gateway.error;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.webhook.IdempotencyConflictException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.apipratudo.gateway")
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex,
      HttpServletRequest request
  ) {
    List<String> details = ex.getBindingResult().getFieldErrors().stream()
        .map(ApiExceptionHandler::formatFieldError)
        .collect(Collectors.toList());
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation error", details, request, ex);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex,
      HttpServletRequest request
  ) {
    List<String> details = ex.getConstraintViolations().stream()
        .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
        .collect(Collectors.toList());
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation error", details, request, ex);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex,
      HttpServletRequest request
  ) {
    String detail = "Invalid value for " + ex.getName();
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation error", List.of(detail), request, ex);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleBadJson(
      HttpMessageNotReadableException ex,
      HttpServletRequest request
  ) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Malformed JSON request", Collections.emptyList(), request,
        ex);
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
    List<String> details = ex.getDetails() == null ? Collections.emptyList() : ex.getDetails();
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), details, request, ex);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), Collections.emptyList(), request, ex);
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
      IdempotencyConflictException ex,
      HttpServletRequest request
  ) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    log.warn("Idempotency conflict key={} traceId={}", ex.getKey(), traceId);
    return error(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), Collections.emptyList(), request, ex);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", Collections.emptyList(),
        request, ex);
  }

  private static ResponseEntity<ErrorResponse> error(
      HttpStatus status,
      String error,
      String message,
      List<String> details,
      HttpServletRequest request,
      Exception ex
  ) {
    List<String> safeDetails = Optional.ofNullable(details).orElse(Collections.emptyList());
    String traceId = TraceIdUtils.resolveTraceId(request);
    ErrorResponse response = new ErrorResponse(error, message, safeDetails, traceId);

    if (status.is5xxServerError()) {
      log.error("Request failed status={} error={} message={} traceId={}", status.value(), error, message, traceId,
          ex);
    } else {
      log.warn("Request failed status={} error={} message={} traceId={}", status.value(), error, message, traceId);
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
