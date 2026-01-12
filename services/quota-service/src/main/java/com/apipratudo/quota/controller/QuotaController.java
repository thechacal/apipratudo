package com.apipratudo.quota.controller;

import com.apipratudo.quota.dto.QuotaConsumeRequest;
import com.apipratudo.quota.dto.QuotaConsumeResponse;
import com.apipratudo.quota.service.QuotaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/quota")
@Validated
@Tag(name = "quota")
public class QuotaController {

  private final QuotaService quotaService;

  public QuotaController(QuotaService quotaService) {
    this.quotaService = quotaService;
  }

  @PostMapping("/consume")
  public ResponseEntity<QuotaConsumeResponse> consume(@Valid @RequestBody QuotaConsumeRequest request) {
    QuotaService.QuotaConsumeResult result = quotaService.consume(request);
    return ResponseEntity.status(result.status()).body(result.response());
  }
}
