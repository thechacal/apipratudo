package com.apipratudo.gateway.loteca.controller;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.loteca.client.LotecaResultsClient;
import com.apipratudo.gateway.loteca.client.LotecaResultsClient.LotecaResultsClientResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/loteca")
public class LotecaResultsController {

  private final LotecaResultsClient client;

  public LotecaResultsController(LotecaResultsClient client) {
    this.client = client;
  }

  @GetMapping("/resultado-oficial")
  public ResponseEntity<String> obterResultado(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    LotecaResultsClientResult result = client.getResultado(traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
