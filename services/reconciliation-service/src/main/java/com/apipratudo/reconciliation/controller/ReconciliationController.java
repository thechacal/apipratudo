package com.apipratudo.reconciliation.controller;

import com.apipratudo.reconciliation.dto.ImportResponse;
import com.apipratudo.reconciliation.dto.MatchRequest;
import com.apipratudo.reconciliation.dto.MatchRunResponse;
import com.apipratudo.reconciliation.dto.PagedResponse;
import com.apipratudo.reconciliation.dto.PaymentWebhookRequest;
import com.apipratudo.reconciliation.dto.PaymentWebhookResponse;
import com.apipratudo.reconciliation.dto.PendingItemResponse;
import com.apipratudo.reconciliation.service.IdempotencyService;
import com.apipratudo.reconciliation.service.ReconciliationService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1")
@Validated
public class ReconciliationController {

  private final ReconciliationService reconciliationService;
  private final IdempotencyService idempotencyService;

  public ReconciliationController(ReconciliationService reconciliationService, IdempotencyService idempotencyService) {
    this.reconciliationService = reconciliationService;
    this.idempotencyService = idempotencyService;
  }

  @PostMapping(value = "/importar-extrato", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> importarExtrato(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "accountId", required = false) String accountId
  ) throws IOException {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyService.IdempotencyResult result = idempotencyService.execute(
          tenantId,
          "POST:/v1/importar-extrato",
          idempotencyKey,
          () -> doImport(tenantId, file, accountId)
      );
      return ResponseEntity.status(result.statusCode()).contentType(MediaType.APPLICATION_JSON).body(result.body());
    }
    return ResponseEntity.ok(doImport(tenantId, file, accountId));
  }

  @PostMapping("/match")
  public ResponseEntity<?> match(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @org.springframework.web.bind.annotation.RequestBody MatchRequest request
  ) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyService.IdempotencyResult result = idempotencyService.execute(
          tenantId,
          "POST:/v1/match",
          idempotencyKey,
          () -> reconciliationService.match(tenantId, request)
      );
      return ResponseEntity.status(result.statusCode()).contentType(MediaType.APPLICATION_JSON).body(result.body());
    }
    MatchRunResponse response = reconciliationService.match(tenantId, request);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/conciliado")
  public ResponseEntity<PagedResponse<com.apipratudo.reconciliation.dto.MatchResponse>> conciliado(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestParam String importId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    return ResponseEntity.ok(reconciliationService.listMatched(tenantId, importId, page, size));
  }

  @GetMapping("/pendencias")
  public ResponseEntity<PagedResponse<PendingItemResponse>> pendencias(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestParam String importId,
      @RequestParam(required = false) String tipo,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    return ResponseEntity.ok(reconciliationService.listPending(tenantId, importId, tipo, page, size));
  }

  @PostMapping("/webhook/pagamento")
  public ResponseEntity<?> webhookPagamento(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @org.springframework.web.bind.annotation.RequestBody PaymentWebhookRequest request
  ) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyService.IdempotencyResult result = idempotencyService.execute(
          tenantId,
          "POST:/v1/webhook/pagamento",
          idempotencyKey,
          () -> reconciliationService.registerPaymentEvent(tenantId, request)
      );
      return ResponseEntity.status(result.statusCode()).contentType(MediaType.APPLICATION_JSON).body(result.body());
    }
    PaymentWebhookResponse response = reconciliationService.registerPaymentEvent(tenantId, request);
    return ResponseEntity.ok(response);
  }

  private ImportResponse doImport(String tenantId, MultipartFile file, String accountId) {
    try {
      return reconciliationService.importStatement(tenantId, accountId, file.getOriginalFilename(), file.getBytes());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read uploaded file", e);
    }
  }
}
