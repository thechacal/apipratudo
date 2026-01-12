package com.apipratudo.quota.controller;

import com.apipratudo.quota.dto.QuotaConsumeRequest;
import com.apipratudo.quota.dto.QuotaConsumeResponse;
import com.apipratudo.quota.dto.QuotaRefundRequest;
import com.apipratudo.quota.dto.QuotaRefundResponse;
import com.apipratudo.quota.dto.QuotaStatusResponse;
import com.apipratudo.quota.service.QuotaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  @SecurityRequirement(name = "InternalToken")
  public ResponseEntity<QuotaConsumeResponse> consume(@Valid @RequestBody QuotaConsumeRequest request) {
    QuotaService.QuotaConsumeResult result = quotaService.consume(request);
    return ResponseEntity.status(result.status()).body(result.response());
  }

  @PostMapping("/refund")
  @SecurityRequirement(name = "InternalToken")
  public ResponseEntity<QuotaRefundResponse> refund(@Valid @RequestBody QuotaRefundRequest request) {
    QuotaService.QuotaRefundResult result = quotaService.refund(request);
    return ResponseEntity.status(result.status()).body(result.response());
  }

  @SecurityRequirement(name = "AdminToken")
  @SecurityRequirement(name = "InternalToken")
  @GetMapping("/status")
  public QuotaStatusResponse status(@RequestParam("apiKey") String apiKey) {
    return quotaService.status(apiKey);
  }
}
