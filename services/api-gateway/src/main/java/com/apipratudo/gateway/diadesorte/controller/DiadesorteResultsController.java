package com.apipratudo.gateway.diadesorte.controller;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.diadesorte.client.DiadesorteResultsClient;
import com.apipratudo.gateway.diadesorte.client.DiadesorteResultsClient.DiadesorteResultsClientResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/diadesorte")
public class DiadesorteResultsController {

  private final DiadesorteResultsClient client;

  public DiadesorteResultsController(DiadesorteResultsClient client) {
    this.client = client;
  }

  @GetMapping("/resultado-oficial")
  public ResponseEntity<String> obterResultado(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    DiadesorteResultsClientResult result = client.getResultado(traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
