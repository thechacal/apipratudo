package com.apipratudo.gateway.megasena.controller;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.megasena.client.MegasenaResultsClient;
import com.apipratudo.gateway.megasena.client.MegasenaResultsClient.MegasenaResultsClientResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/megasena")
public class MegasenaResultsController {

  private final MegasenaResultsClient client;

  public MegasenaResultsController(MegasenaResultsClient client) {
    this.client = client;
  }

  @GetMapping("/resultado-oficial")
  public ResponseEntity<String> obterResultado(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    MegasenaResultsClientResult result = client.getResultado(traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
