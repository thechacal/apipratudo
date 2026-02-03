package com.apipratudo.reconciliation.service;

import com.apipratudo.reconciliation.config.ReconciliationProperties;
import com.apipratudo.reconciliation.dto.ImportResponse;
import com.apipratudo.reconciliation.dto.MatchRequest;
import com.apipratudo.reconciliation.dto.MatchResponse;
import com.apipratudo.reconciliation.dto.MatchRunResponse;
import com.apipratudo.reconciliation.dto.PagedResponse;
import com.apipratudo.reconciliation.dto.PaymentWebhookRequest;
import com.apipratudo.reconciliation.dto.PaymentWebhookResponse;
import com.apipratudo.reconciliation.dto.PendingItemResponse;
import com.apipratudo.reconciliation.error.ApiException;
import com.apipratudo.reconciliation.model.BankTransaction;
import com.apipratudo.reconciliation.model.MatchResult;
import com.apipratudo.reconciliation.model.PaymentEvent;
import com.apipratudo.reconciliation.model.StatementImport;
import com.apipratudo.reconciliation.repository.ReconciliationStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReconciliationService {

  private final ReconciliationStore store;
  private final StatementParserService parserService;
  private final ReconciliationProperties properties;
  private final Clock clock;

  public ReconciliationService(
      ReconciliationStore store,
      StatementParserService parserService,
      ReconciliationProperties properties,
      Clock clock
  ) {
    this.store = store;
    this.parserService = parserService;
    this.properties = properties;
    this.clock = clock;
  }

  public ImportResponse importStatement(String tenantId, String accountId, String filename, byte[] content) {
    String importId = "imp_" + UUID.randomUUID().toString().replace("-", "");
    ParsedStatement parsed = parserService.parse(tenantId, importId, filename, content);

    StatementImport statementImport = new StatementImport(
        importId,
        tenantId,
        StringUtils.hasText(accountId) ? accountId : properties.getDefaultAccountId(),
        parsed.format(),
        parsed.periodStart(),
        parsed.periodEnd(),
        Instant.now(clock),
        parsed.transactions().size(),
        parsed.totalCreditsCents(),
        parsed.totalDebitsCents()
    );

    store.saveImport(statementImport);
    store.saveTransactions(parsed.transactions());

    return new ImportResponse(importId, parsed.format(), statementImport.createdAt(),
        parsed.transactions().size(), parsed.totalCreditsCents(), parsed.totalDebitsCents());
  }

  public PaymentWebhookResponse registerPaymentEvent(String tenantId, PaymentWebhookRequest request) {
    PaymentEvent event = new PaymentEvent(
        request.eventId(),
        tenantId,
        request.paidAt(),
        request.amountCents(),
        request.reference(),
        request.providerPaymentId(),
        request.raw() == null ? java.util.Map.of() : request.raw()
    );
    store.savePaymentEvent(event);
    return new PaymentWebhookResponse(true, request.eventId());
  }

  public MatchRunResponse match(String tenantId, MatchRequest request) {
    StatementImport statementImport = store.getImport(tenantId, request.importId());
    if (statementImport == null) {
      throw new ApiException(404, "NOT_FOUND", "Import not found");
    }

    List<BankTransaction> txs = store.listTransactions(tenantId, request.importId());
    List<PaymentEvent> events = store.listPaymentEvents(tenantId);

    long amountTolerance = request.ruleset().amountToleranceCents() == null ? 0 : request.ruleset().amountToleranceCents();
    int dateTolerance = request.ruleset().dateToleranceDays() == null ? 2 : request.ruleset().dateToleranceDays();
    boolean byRef = request.ruleset().matchBy().stream()
        .map(v -> v.toLowerCase(Locale.ROOT)).anyMatch("reference"::equals);

    int matched = 0;
    for (PaymentEvent event : events) {
      if (!store.listMatchesByEventId(tenantId, event.id()).isEmpty()) {
        continue;
      }
      BankTransaction candidate = findCandidate(tenantId, txs, event, amountTolerance, dateTolerance, byRef);
      if (candidate == null) {
        continue;
      }
      MatchResult result = new MatchResult(
          "mtc_" + UUID.randomUUID().toString().replace("-", ""),
          tenantId,
          request.importId(),
          candidate.id(),
          event.id(),
          Instant.now(clock),
          byRef ? "amount+date+reference" : "amount+date",
          byRef ? 0.95d : 0.85d
      );
      store.saveMatch(result);
      matched++;
    }

    int pendingTx = listPending(tenantId, request.importId(), "transactions", 1, Integer.MAX_VALUE).items().size();
    int pendingEvents = listPending(tenantId, request.importId(), "events", 1, Integer.MAX_VALUE).items().size();

    return new MatchRunResponse(request.importId(), matched, pendingTx, pendingEvents);
  }

  private BankTransaction findCandidate(
      String tenantId,
      List<BankTransaction> txs,
      PaymentEvent event,
      long amountTolerance,
      int dateTolerance,
      boolean byRef
  ) {
    for (BankTransaction tx : txs) {
      if (!store.listMatchesByTxId(tenantId, tx.id()).isEmpty()) {
        continue;
      }
      long diff = Math.abs(tx.amountCents() - event.amountCents());
      if (diff > amountTolerance) {
        continue;
      }
      LocalDate paidDate = event.paidAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();
      long dayDiff = Math.abs(ChronoUnit.DAYS.between(tx.date(), paidDate));
      if (dayDiff > dateTolerance) {
        continue;
      }
      if (byRef) {
        String txRef = normalize(tx.reference());
        String eventRef = normalize(event.reference());
        if (!(txRef.contains(eventRef) || eventRef.contains(txRef))) {
          continue;
        }
      }
      return tx;
    }
    return null;
  }

  public PagedResponse<MatchResponse> listMatched(String tenantId, String importId, int page, int size) {
    List<MatchResponse> all = store.listMatches(tenantId, importId).stream()
        .sorted(Comparator.comparing(MatchResult::matchedAt).reversed())
        .map(this::toMatchResponse)
        .collect(Collectors.toList());
    return page(all, page, size);
  }

  public PagedResponse<PendingItemResponse> listPending(String tenantId, String importId, String type, int page, int size) {
    List<PendingItemResponse> items = new ArrayList<>();
    if (!"events".equalsIgnoreCase(type)) {
      for (BankTransaction tx : store.listTransactions(tenantId, importId)) {
        if (store.listMatchesByTxId(tenantId, tx.id()).isEmpty()) {
          items.add(new PendingItemResponse(
              "TRANSACTION", tx.id(), tx.date().toString(), tx.amountCents(), tx.reference(), tx.description()
          ));
        }
      }
    }
    if (!"transactions".equalsIgnoreCase(type)) {
      for (PaymentEvent event : store.listPaymentEvents(tenantId)) {
        if (store.listMatchesByEventId(tenantId, event.id()).isEmpty()) {
          items.add(new PendingItemResponse(
              "EVENT",
              event.id(),
              event.paidAt() == null ? null : event.paidAt().toString(),
              event.amountCents(),
              event.reference(),
              event.providerPaymentId()
          ));
        }
      }
    }
    items.sort(Comparator.comparing(PendingItemResponse::itemId));
    return page(items, page, size);
  }

  private MatchResponse toMatchResponse(MatchResult m) {
    return new MatchResponse(m.id(), m.importId(), m.txId(), m.eventId(), m.ruleApplied(), m.confidence(),
        m.matchedAt() == null ? null : m.matchedAt().toString());
  }

  private <T> PagedResponse<T> page(List<T> all, int page, int size) {
    int safePage = Math.max(page, 1);
    int safeSize = Math.max(size, 1);
    int from = (safePage - 1) * safeSize;
    if (from >= all.size()) {
      return new PagedResponse<>(List.of(), safePage, safeSize, all.size());
    }
    int to = Math.min(from + safeSize, all.size());
    return new PagedResponse<>(all.subList(from, to), safePage, safeSize, all.size());
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }
}
