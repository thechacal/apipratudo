package com.apipratudo.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.reconciliation.config.ReconciliationProperties;
import com.apipratudo.reconciliation.dto.MatchRequest;
import com.apipratudo.reconciliation.dto.PaymentWebhookRequest;
import com.apipratudo.reconciliation.dto.Ruleset;
import com.apipratudo.reconciliation.repository.InMemoryReconciliationStore;
import com.apipratudo.reconciliation.service.ReconciliationService;
import com.apipratudo.reconciliation.service.StatementParserService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReconciliationServiceTest {

  @Test
  void matchingBasicFlowWorks() {
    InMemoryReconciliationStore store = new InMemoryReconciliationStore();
    ReconciliationProperties props = new ReconciliationProperties();
    ReconciliationService service = new ReconciliationService(store, new StatementParserService(), props,
        Clock.fixed(Instant.parse("2026-02-02T10:00:00Z"), ZoneOffset.UTC));

    String csv = "date,amount,description,reference\n2026-02-01,100.00,Pix recebido,pedido-1\n";
    var importResponse = service.importStatement("tenant-1", "main", "e.csv", csv.getBytes(StandardCharsets.UTF_8));

    service.registerPaymentEvent("tenant-1", new PaymentWebhookRequest(
        "evt-1",
        Instant.parse("2026-02-01T12:00:00Z"),
        10000L,
        "pedido-1",
        "prov-1",
        Map.of("foo", "bar")
    ));

    var result = service.match("tenant-1", new MatchRequest(importResponse.importId(),
        new Ruleset(List.of("amount", "date", "reference"), 2, 0L, true)));

    assertThat(result.matchedCount()).isEqualTo(1);
    assertThat(service.listMatched("tenant-1", importResponse.importId(), 1, 20).items()).hasSize(1);
    assertThat(service.listPending("tenant-1", importResponse.importId(), null, 1, 20).items()).isEmpty();
  }
}
