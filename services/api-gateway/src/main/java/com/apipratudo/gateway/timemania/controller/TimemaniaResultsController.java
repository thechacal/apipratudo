package com.apipratudo.gateway.timemania.controller;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.timemania.client.TimemaniaResultsClient;
import com.apipratudo.gateway.timemania.client.TimemaniaResultsClient.TimemaniaResultsClientResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/timemania")
public class TimemaniaResultsController {

  private final TimemaniaResultsClient client;

  public TimemaniaResultsController(TimemaniaResultsClient client) {
    this.client = client;
  }

  @GetMapping("/resultado-oficial")
  public ResponseEntity<String> obterResultado(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    TimemaniaResultsClientResult result = client.getResultado(traceId);
    return ResponseEntity.status(result.statusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
