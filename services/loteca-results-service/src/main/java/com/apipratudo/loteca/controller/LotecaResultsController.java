package com.apipratudo.loteca.controller;

import com.apipratudo.loteca.dto.LotecaResultadoOficialResponse;
import com.apipratudo.loteca.logging.TraceIdUtils;
import com.apipratudo.loteca.service.LotecaResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/loteca")
public class LotecaResultsController {

  private final LotecaResultsService service;

  public LotecaResultsController(LotecaResultsService service) {
    this.service = service;
  }

  @GetMapping("/resultado-oficial")
  public LotecaResultadoOficialResponse obterResultadoLoteca(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return service.obterResultadoOficial(traceId);
  }
}
