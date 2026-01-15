package com.apipratudo.timemania.error;

import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.apipratudo.timemania")
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(UpstreamTimeoutException.class)
  public ResponseEntity<ApiErrorResponse> handleTimeout(UpstreamTimeoutException ex) {
    ApiErrorResponse body = new ApiErrorResponse(
        "UPSTREAM_TIMEOUT",
        "Falha ao consultar resultado oficial da CAIXA (timeout)",
        Collections.emptyList()
    );
    log.warn("Upstream timeout error={}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  @ExceptionHandler(UpstreamBadResponseException.class)
  public ResponseEntity<ApiErrorResponse> handleBadResponse(UpstreamBadResponseException ex) {
    List<String> details = ex.getDetails() == null ? Collections.emptyList() : ex.getDetails();
    ApiErrorResponse body = new ApiErrorResponse(
        "UPSTREAM_BAD_RESPONSE",
        "Falha ao consultar resultado oficial da CAIXA",
        details
    );
    log.warn("Upstream bad response error={} details={}", ex.getMessage(), details);
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
    ApiErrorResponse body = new ApiErrorResponse(
        "UPSTREAM_BAD_RESPONSE",
        "Falha ao consultar resultado oficial da CAIXA",
        List.of("Erro inesperado")
    );
    log.error("Unhandled error", ex);
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}
