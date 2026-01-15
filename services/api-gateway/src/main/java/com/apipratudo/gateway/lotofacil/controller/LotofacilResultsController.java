package com.apipratudo.gateway.lotofacil.controller;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.lotofacil.client.LotofacilResultsClient;
import com.apipratudo.gateway.lotofacil.client.LotofacilResultsClient.LotofacilResultsClientResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/lotofacil")
public class LotofacilResultsController {

  private final LotofacilResultsClient client;

  public LotofacilResultsController(LotofacilResultsClient client) {
    this.client = client;
  }

  @GetMapping("/resultado-oficial")
  public ResponseEntity<String> obterResultado(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    LotofacilResultsClientResult result = client.getResultado(traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
