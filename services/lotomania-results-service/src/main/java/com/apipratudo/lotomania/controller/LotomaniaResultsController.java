package com.apipratudo.lotomania.controller;

import com.apipratudo.lotomania.dto.LotomaniaResultadoOficialResponse;
import com.apipratudo.lotomania.logging.TraceIdUtils;
import com.apipratudo.lotomania.service.LotomaniaResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/lotomania")
public class LotomaniaResultsController {

  private final LotomaniaResultsService service;

  public LotomaniaResultsController(LotomaniaResultsService service) {
    this.service = service;
  }

  @GetMapping("/resultado-oficial")
  public LotomaniaResultadoOficialResponse obterResultadoLotomania(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return service.obterResultadoOficial(traceId);
  }
}
