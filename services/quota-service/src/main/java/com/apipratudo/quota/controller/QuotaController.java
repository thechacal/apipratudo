package com.apipratudo.quota.controller;

import com.apipratudo.quota.dto.QuotaConsumeRequest;
import com.apipratudo.quota.dto.QuotaConsumeResponse;
import com.apipratudo.quota.dto.QuotaRefundRequest;
import com.apipratudo.quota.dto.QuotaRefundResponse;
import com.apipratudo.quota.dto.QuotaStatusResponse;
import com.apipratudo.quota.service.QuotaService;
import com.apipratudo.quota.security.SecurityTokenService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/v1/quota")
@Validated
@Tag(name = "quota")
public class QuotaController {

  private final QuotaService quotaService;
  private final SecurityTokenService tokenService;

  public QuotaController(QuotaService quotaService, SecurityTokenService tokenService) {
    this.quotaService = quotaService;
    this.tokenService = tokenService;
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
  @SecurityRequirement(name = "ApiKeyAuth")
  @GetMapping("/status")
  public QuotaStatusResponse status(
      @RequestParam(value = "apiKey", required = false) String apiKey,
      @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
      HttpServletRequest request
  ) {
    String resolved = apiKeyHeader;
    if (StringUtils.hasText(apiKey)) {
      if (tokenService.isAdmin(request) || tokenService.isInternal(request)) {
        resolved = apiKey;
      } else if (!StringUtils.hasText(apiKeyHeader)) {
        throw new IllegalArgumentException("Missing X-Api-Key");
      }
    }
    if (!StringUtils.hasText(resolved)) {
      throw new IllegalArgumentException("Missing X-Api-Key");
    }
    return quotaService.status(resolved);
  }
}
