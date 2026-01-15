package com.apipratudo.gateway.quina.controller;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.quina.client.QuinaResultsClient;
import com.apipratudo.gateway.quina.client.QuinaResultsClient.QuinaResultsClientResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/quina")
public class QuinaResultsController {

  private final QuinaResultsClient client;

  public QuinaResultsController(QuinaResultsClient client) {
    this.client = client;
  }

  @GetMapping("/resultado-oficial")
  public ResponseEntity<String> obterResultado(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    QuinaResultsClientResult result = client.getResultado(traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
