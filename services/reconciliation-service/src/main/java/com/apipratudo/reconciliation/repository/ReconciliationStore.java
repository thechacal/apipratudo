package com.apipratudo.reconciliation.repository;

import com.apipratudo.reconciliation.model.BankTransaction;
import com.apipratudo.reconciliation.model.IdempotencyRecord;
import com.apipratudo.reconciliation.model.MatchResult;
import com.apipratudo.reconciliation.model.PaymentEvent;
import com.apipratudo.reconciliation.model.StatementImport;
import java.util.List;

public interface ReconciliationStore {

  StatementImport saveImport(StatementImport statementImport);

  StatementImport getImport(String tenantId, String importId);

  List<BankTransaction> saveTransactions(List<BankTransaction> transactions);

  List<BankTransaction> listTransactions(String tenantId, String importId);

  PaymentEvent savePaymentEvent(PaymentEvent event);

  List<PaymentEvent> listPaymentEvents(String tenantId);

  MatchResult saveMatch(MatchResult matchResult);

  List<MatchResult> listMatches(String tenantId, String importId);

  List<MatchResult> listMatchesByEventId(String tenantId, String eventId);

  List<MatchResult> listMatchesByTxId(String tenantId, String txId);

  IdempotencyRecord getIdempotency(String key);

  void saveIdempotency(IdempotencyRecord record);
}
