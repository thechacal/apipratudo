package com.apipratudo.supersete.controller;

import com.apipratudo.supersete.dto.SuperseteResultadoOficialResponse;
import com.apipratudo.supersete.logging.TraceIdUtils;
import com.apipratudo.supersete.service.SuperseteResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/supersete")
public class SuperseteResultsController {

  private final SuperseteResultsService service;

  public SuperseteResultsController(SuperseteResultsService service) {
    this.service = service;
  }

  @GetMapping("/resultado-oficial")
  public SuperseteResultadoOficialResponse obterResultadoSupersete(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return service.obterResultadoOficial(traceId);
  }
}
