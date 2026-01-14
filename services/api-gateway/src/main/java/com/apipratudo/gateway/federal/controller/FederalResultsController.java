package com.apipratudo.gateway.federal.controller;

import com.apipratudo.gateway.federal.client.FederalResultsClient;
import com.apipratudo.gateway.federal.client.FederalResultsClient.FederalResultsClientResult;
import com.apipratudo.gateway.logging.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/federal")
public class FederalResultsController {

  private final FederalResultsClient client;

  public FederalResultsController(FederalResultsClient client) {
    this.client = client;
  }

  @GetMapping("/resultado-oficial")
  public ResponseEntity<String> obterResultado(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    FederalResultsClientResult result = client.getResultado(traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
