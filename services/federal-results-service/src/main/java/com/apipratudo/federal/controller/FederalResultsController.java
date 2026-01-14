package com.apipratudo.federal.controller;

import com.apipratudo.federal.dto.ResultadoOficialResponse;
import com.apipratudo.federal.logging.TraceIdUtils;
import com.apipratudo.federal.service.FederalResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/federal")
public class FederalResultsController {

  private final FederalResultsService service;

  public FederalResultsController(FederalResultsService service) {
    this.service = service;
  }

  @GetMapping("/resultado-oficial")
  public ResultadoOficialResponse obterResultadoFederal(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return service.obterResultadoOficial(traceId);
  }
}
