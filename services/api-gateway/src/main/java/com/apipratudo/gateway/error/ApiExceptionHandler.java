package com.apipratudo.gateway.error;

import com.apipratudo.gateway.webhook.IdempotencyConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.apipratudo.gateway")
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String detail = ex.getBindingResult().getFieldErrors().stream()
        .map(ApiExceptionHandler::formatFieldError)
        .findFirst()
        .orElse("Validation error");

    return problem(
        HttpStatus.BAD_REQUEST,
        "https://apipratudo.local/errors/validation",
        "Validation error",
        detail,
        request
    );
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    String detail = ex.getConstraintViolations().stream()
        .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
        .findFirst()
        .orElse("Validation error");

    return problem(
        HttpStatus.BAD_REQUEST,
        "https://apipratudo.local/errors/validation",
        "Validation error",
        detail,
        request
    );
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleBadJson(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "https://apipratudo.local/errors/bad-request",
        "Bad request",
        "Malformed JSON request",
        request
    );
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ProblemDetail> handleIdempotencyConflict(
      IdempotencyConflictException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "https://apipratudo.local/errors/idempotency-conflict",
        "Idempotency conflict",
        ex.getMessage(),
        request
    );
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(
      Exception ex, HttpServletRequest request) {
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "https://apipratudo.local/errors/internal",
        "Internal server error",
        "Unexpected error",
        request
    );
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status,
      String type,
      String title,
      String detail,
      HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setType(URI.create(type));
    problem.setTitle(title);
    problem.setDetail(detail);
    if (request != null) {
      problem.setInstance(URI.create(request.getRequestURI()));
    }

    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  private static String formatFieldError(FieldError error) {
    String message = Optional.ofNullable(error.getDefaultMessage())
        .orElse("is invalid");
    return error.getField() + " " + message;
  }
}
