package com.apipratudo.diadesorte.controller;

import com.apipratudo.diadesorte.dto.DiadesorteResultadoOficialResponse;
import com.apipratudo.diadesorte.logging.TraceIdUtils;
import com.apipratudo.diadesorte.service.DiadesorteResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/diadesorte")
public class DiadesorteResultsController {

  private final DiadesorteResultsService service;

  public DiadesorteResultsController(DiadesorteResultsService service) {
    this.service = service;
  }

  @GetMapping("/resultado-oficial")
  public DiadesorteResultadoOficialResponse obterResultadoDiadesorte(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return service.obterResultadoOficial(traceId);
  }
}
