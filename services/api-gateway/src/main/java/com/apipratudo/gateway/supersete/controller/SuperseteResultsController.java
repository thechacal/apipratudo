package com.apipratudo.gateway.supersete.controller;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.supersete.client.SuperseteResultsClient;
import com.apipratudo.gateway.supersete.client.SuperseteResultsClient.SuperseteResultsClientResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/supersete")
public class SuperseteResultsController {

  private final SuperseteResultsClient client;

  public SuperseteResultsController(SuperseteResultsClient client) {
    this.client = client;
  }

  @GetMapping("/resultado-oficial")
  public ResponseEntity<String> obterResultado(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    SuperseteResultsClientResult result = client.getResultado(traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
