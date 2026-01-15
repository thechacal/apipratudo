package com.apipratudo.quina.controller;

import com.apipratudo.quina.dto.QuinaResultadoOficialResponse;
import com.apipratudo.quina.logging.TraceIdUtils;
import com.apipratudo.quina.service.QuinaResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/quina")
public class QuinaResultsController {

  private final QuinaResultsService service;

  public QuinaResultsController(QuinaResultsService service) {
    this.service = service;
  }

  @GetMapping("/resultado-oficial")
  public QuinaResultadoOficialResponse obterResultadoQuina(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return service.obterResultadoOficial(traceId);
  }
}
