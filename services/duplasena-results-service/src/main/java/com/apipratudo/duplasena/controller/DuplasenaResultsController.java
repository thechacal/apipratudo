package com.apipratudo.duplasena.controller;

import com.apipratudo.duplasena.dto.DuplasenaResultadoOficialResponse;
import com.apipratudo.duplasena.logging.TraceIdUtils;
import com.apipratudo.duplasena.service.DuplasenaResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/duplasena")
public class DuplasenaResultsController {

  private final DuplasenaResultsService service;

  public DuplasenaResultsController(DuplasenaResultsService service) {
    this.service = service;
  }

  @GetMapping("/resultado-oficial")
  public DuplasenaResultadoOficialResponse obterResultadoDuplasena(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return service.obterResultadoOficial(traceId);
  }
}
