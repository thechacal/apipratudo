package com.apipratudo.gateway.maismilionaria.controller;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.maismilionaria.client.MaismilionariaResultsClient;
import com.apipratudo.gateway.maismilionaria.client.MaismilionariaResultsClient.MaismilionariaResultsClientResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/maismilionaria")
public class MaismilionariaResultsController {

  private final MaismilionariaResultsClient client;

  public MaismilionariaResultsController(MaismilionariaResultsClient client) {
    this.client = client;
  }

  @GetMapping("/resultado-oficial")
  public ResponseEntity<String> obterResultado(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    MaismilionariaResultsClientResult result = client.getResultado(traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
