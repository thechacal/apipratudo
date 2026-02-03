package com.apipratudo.reconciliation.repository;

import com.apipratudo.reconciliation.model.BankTransaction;
import com.apipratudo.reconciliation.model.IdempotencyRecord;
import com.apipratudo.reconciliation.model.MatchResult;
import com.apipratudo.reconciliation.model.PaymentEvent;
import com.apipratudo.reconciliation.model.StatementImport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryReconciliationStore implements ReconciliationStore {

  private final Map<String, StatementImport> imports = new ConcurrentHashMap<>();
  private final Map<String, BankTransaction> transactions = new ConcurrentHashMap<>();
  private final Map<String, PaymentEvent> events = new ConcurrentHashMap<>();
  private final Map<String, MatchResult> matches = new ConcurrentHashMap<>();
  private final Map<String, IdempotencyRecord> idempotency = new ConcurrentHashMap<>();

  @Override
  public StatementImport saveImport(StatementImport statementImport) {
    imports.put(statementImport.tenantId() + ":" + statementImport.id(), statementImport);
    return statementImport;
  }

  @Override
  public StatementImport getImport(String tenantId, String importId) {
    return imports.get(tenantId + ":" + importId);
  }

  @Override
  public List<BankTransaction> saveTransactions(List<BankTransaction> txs) {
    for (BankTransaction tx : txs) {
      transactions.put(tx.tenantId() + ":" + tx.id(), tx);
    }
    return txs;
  }

  @Override
  public List<BankTransaction> listTransactions(String tenantId, String importId) {
    List<BankTransaction> out = new ArrayList<>();
    for (BankTransaction tx : transactions.values()) {
      if (tx.tenantId().equals(tenantId) && tx.importId().equals(importId)) {
        out.add(tx);
      }
    }
    return out;
  }

  @Override
  public PaymentEvent savePaymentEvent(PaymentEvent event) {
    events.put(event.tenantId() + ":" + event.id(), event);
    return event;
  }

  @Override
  public List<PaymentEvent> listPaymentEvents(String tenantId) {
    List<PaymentEvent> out = new ArrayList<>();
    for (PaymentEvent event : events.values()) {
      if (event.tenantId().equals(tenantId)) {
        out.add(event);
      }
    }
    return out;
  }

  @Override
  public MatchResult saveMatch(MatchResult matchResult) {
    matches.put(matchResult.tenantId() + ":" + matchResult.id(), matchResult);
    return matchResult;
  }

  @Override
  public List<MatchResult> listMatches(String tenantId, String importId) {
    List<MatchResult> out = new ArrayList<>();
    for (MatchResult match : matches.values()) {
      if (match.tenantId().equals(tenantId) && match.importId().equals(importId)) {
        out.add(match);
      }
    }
    return out;
  }

  @Override
  public List<MatchResult> listMatchesByEventId(String tenantId, String eventId) {
    List<MatchResult> out = new ArrayList<>();
    for (MatchResult match : matches.values()) {
      if (match.tenantId().equals(tenantId) && eventId.equals(match.eventId())) {
        out.add(match);
      }
    }
    return out;
  }

  @Override
  public List<MatchResult> listMatchesByTxId(String tenantId, String txId) {
    List<MatchResult> out = new ArrayList<>();
    for (MatchResult match : matches.values()) {
      if (match.tenantId().equals(tenantId) && txId.equals(match.txId())) {
        out.add(match);
      }
    }
    return out;
  }

  @Override
  public IdempotencyRecord getIdempotency(String key) {
    IdempotencyRecord record = idempotency.get(key);
    if (record == null) {
      return null;
    }
    if (record.expiresAt() != null && record.expiresAt().isBefore(Instant.now())) {
      idempotency.remove(key);
      return null;
    }
    return record;
  }

  @Override
  public void saveIdempotency(IdempotencyRecord record) {
    idempotency.put(record.key(), record);
  }
}
