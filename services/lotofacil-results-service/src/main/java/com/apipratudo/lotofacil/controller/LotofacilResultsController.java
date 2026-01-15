package com.apipratudo.lotofacil.controller;

import com.apipratudo.lotofacil.dto.LotofacilResultadoOficialResponse;
import com.apipratudo.lotofacil.logging.TraceIdUtils;
import com.apipratudo.lotofacil.service.LotofacilResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/lotofacil")
public class LotofacilResultsController {

  private final LotofacilResultsService service;

  public LotofacilResultsController(LotofacilResultsService service) {
    this.service = service;
  }

  @GetMapping("/resultado-oficial")
  public LotofacilResultadoOficialResponse obterResultadoLotofacil(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return service.obterResultadoOficial(traceId);
  }
}
