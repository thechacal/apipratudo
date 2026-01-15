package com.apipratudo.timemania.controller;

import com.apipratudo.timemania.dto.TimemaniaResultadoOficialResponse;
import com.apipratudo.timemania.logging.TraceIdUtils;
import com.apipratudo.timemania.service.TimemaniaResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/timemania")
public class TimemaniaResultsController {

  private final TimemaniaResultsService service;

  public TimemaniaResultsController(TimemaniaResultsService service) {
    this.service = service;
  }

  @GetMapping("/resultado-oficial")
  public TimemaniaResultadoOficialResponse obterResultadoTimemania(HttpServletRequest request) {
    String traceId = TraceIdUtils.resolveTraceId(request);
    return service.obterResultadoOficial(traceId);
  }
}
