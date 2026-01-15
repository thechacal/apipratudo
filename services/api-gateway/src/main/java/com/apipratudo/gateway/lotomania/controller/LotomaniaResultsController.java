package com.apipratudo.gateway.lotomania.controller;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.lotomania.client.LotomaniaResultsClient;
import com.apipratudo.gateway.lotomania.client.LotomaniaResultsClient.LotomaniaResultsClientResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/lotomania")
public class LotomaniaResultsController {

  private final LotomaniaResultsClient client;

  public LotomaniaResultsController(LotomaniaResultsClient client) {
    this.client = client;
  }

  @GetMapping("/resultado-oficial")
  public ResponseEntity<String> obterResultado(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    LotomaniaResultsClientResult result = client.getResultado(traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
