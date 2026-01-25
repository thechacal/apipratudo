package com.apipratudo.billingsaas.controller;

import com.apipratudo.billingsaas.dto.ReportSummaryResponse;
import com.apipratudo.billingsaas.model.ReportSummary;
import com.apipratudo.billingsaas.service.ChargeService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/relatorios")
@Validated
@Tag(name = "relatorios")
@SecurityRequirement(name = "ServiceToken")
public class InternalReportsController {

  private final ChargeService chargeService;
  private final Clock clock;

  public InternalReportsController(ChargeService chargeService, Clock clock) {
    this.chargeService = chargeService;
    this.clock = clock;
  }

  @GetMapping
  public ResponseEntity<ReportSummaryResponse> summary(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to
  ) {
    LocalDate toDate = parseDateOrDefault(to, LocalDate.now(clock));
    LocalDate fromDate = parseDateOrDefault(from, toDate.minusDays(30));

    ReportSummary summary = chargeService.reportSummary(tenantId, fromDate, toDate);
    ReportSummaryResponse response = new ReportSummaryResponse();
    response.setFrom(summary.from());
    response.setTo(summary.to());
    response.setCountTotal(summary.countTotal());
    response.setCountPaid(summary.countPaid());
    response.setCountPending(summary.countPending());
    response.setTotalCents(summary.totalCents());
    response.setPaidCents(summary.paidCents());
    response.setPendingCents(summary.pendingCents());

    return ResponseEntity.ok(response);
  }

  private LocalDate parseDateOrDefault(String value, LocalDate fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return LocalDate.parse(value);
  }
}
