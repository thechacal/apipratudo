package com.apipratudo.megasena.controller;

import com.apipratudo.megasena.dto.MegasenaResultadoOficialResponse;
import com.apipratudo.megasena.logging.TraceIdUtils;
import com.apipratudo.megasena.service.MegasenaResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/megasena")
public class MegasenaResultsController {

  private final MegasenaResultsService service;

  public MegasenaResultsController(MegasenaResultsService service) {
    this.service = service;
  }

  @GetMapping("/resultado-oficial")
  public MegasenaResultadoOficialResponse obterResultadoMegasena(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return service.obterResultadoOficial(traceId);
  }
}
