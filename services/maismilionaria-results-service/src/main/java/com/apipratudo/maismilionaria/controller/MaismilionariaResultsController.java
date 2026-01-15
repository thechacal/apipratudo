package com.apipratudo.maismilionaria.controller;

import com.apipratudo.maismilionaria.dto.MaismilionariaResultadoOficialResponse;
import com.apipratudo.maismilionaria.logging.TraceIdUtils;
import com.apipratudo.maismilionaria.service.MaismilionariaResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/maismilionaria")
public class MaismilionariaResultsController {

  private final MaismilionariaResultsService service;

  public MaismilionariaResultsController(MaismilionariaResultsService service) {
    this.service = service;
  }

  @GetMapping("/resultado-oficial")
  public MaismilionariaResultadoOficialResponse obterResultadoMaismilionaria(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return service.obterResultadoOficial(traceId);
  }
}
