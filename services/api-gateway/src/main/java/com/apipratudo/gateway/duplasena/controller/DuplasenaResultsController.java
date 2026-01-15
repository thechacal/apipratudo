package com.apipratudo.gateway.duplasena.controller;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.duplasena.client.DuplasenaResultsClient;
import com.apipratudo.gateway.duplasena.client.DuplasenaResultsClient.DuplasenaResultsClientResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/duplasena")
public class DuplasenaResultsController {

  private final DuplasenaResultsClient client;

  public DuplasenaResultsController(DuplasenaResultsClient client) {
    this.client = client;
  }

  @GetMapping("/resultado-oficial")
  public ResponseEntity<String> obterResultado(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    DuplasenaResultsClientResult result = client.getResultado(traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
