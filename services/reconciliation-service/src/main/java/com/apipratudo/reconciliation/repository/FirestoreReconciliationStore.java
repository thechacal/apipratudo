package com.apipratudo.reconciliation.repository;

import com.apipratudo.reconciliation.config.IdempotencyProperties;
import com.apipratudo.reconciliation.config.ReconciliationProperties;
import com.apipratudo.reconciliation.model.BankTransaction;
import com.apipratudo.reconciliation.model.IdempotencyRecord;
import com.apipratudo.reconciliation.model.MatchResult;
import com.apipratudo.reconciliation.model.PaymentEvent;
import com.apipratudo.reconciliation.model.StatementImport;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreReconciliationStore implements ReconciliationStore {

  private final Firestore firestore;
  private final ReconciliationProperties props;
  private final IdempotencyProperties idemProps;

  public FirestoreReconciliationStore(
      Firestore firestore,
      ReconciliationProperties props,
      IdempotencyProperties idemProps
  ) {
    this.firestore = firestore;
    this.props = props;
    this.idemProps = idemProps;
  }

  @Override
  public StatementImport saveImport(StatementImport statementImport) {
    set(props.getImportsCollection(), statementImport.tenantId() + ":" + statementImport.id(), mapImport(statementImport));
    return statementImport;
  }

  @Override
  public StatementImport getImport(String tenantId, String importId) {
    DocumentSnapshot doc = get(props.getImportsCollection(), tenantId + ":" + importId);
    if (doc == null || !doc.exists()) {
      return null;
    }
    return fromImport(doc.getData());
  }

  @Override
  public List<BankTransaction> saveTransactions(List<BankTransaction> txs) {
    for (BankTransaction tx : txs) {
      set(props.getTransactionsCollection(), tx.tenantId() + ":" + tx.id(), mapTx(tx));
    }
    return txs;
  }

  @Override
  public List<BankTransaction> listTransactions(String tenantId, String importId) {
    Query query = firestore.collection(props.getTransactionsCollection())
        .whereEqualTo("tenantId", tenantId)
        .whereEqualTo("importId", importId);
    List<BankTransaction> out = new ArrayList<>();
    for (DocumentSnapshot doc : execute(query)) {
      out.add(fromTx(doc.getData()));
    }
    return out;
  }

  @Override
  public PaymentEvent savePaymentEvent(PaymentEvent event) {
    set(props.getEventsCollection(), event.tenantId() + ":" + event.id(), mapEvent(event));
    return event;
  }

  @Override
  public List<PaymentEvent> listPaymentEvents(String tenantId) {
    Query query = firestore.collection(props.getEventsCollection()).whereEqualTo("tenantId", tenantId);
    List<PaymentEvent> out = new ArrayList<>();
    for (DocumentSnapshot doc : execute(query)) {
      out.add(fromEvent(doc.getData()));
    }
    return out;
  }

  @Override
  public MatchResult saveMatch(MatchResult matchResult) {
    set(props.getMatchesCollection(), matchResult.tenantId() + ":" + matchResult.id(), mapMatch(matchResult));
    return matchResult;
  }

  @Override
  public List<MatchResult> listMatches(String tenantId, String importId) {
    Query query = firestore.collection(props.getMatchesCollection())
        .whereEqualTo("tenantId", tenantId)
        .whereEqualTo("importId", importId);
    List<MatchResult> out = new ArrayList<>();
    for (DocumentSnapshot doc : execute(query)) {
      out.add(fromMatch(doc.getData()));
    }
    return out;
  }

  @Override
  public List<MatchResult> listMatchesByEventId(String tenantId, String eventId) {
    Query query = firestore.collection(props.getMatchesCollection())
        .whereEqualTo("tenantId", tenantId)
        .whereEqualTo("eventId", eventId);
    List<MatchResult> out = new ArrayList<>();
    for (DocumentSnapshot doc : execute(query)) {
      out.add(fromMatch(doc.getData()));
    }
    return out;
  }

  @Override
  public List<MatchResult> listMatchesByTxId(String tenantId, String txId) {
    Query query = firestore.collection(props.getMatchesCollection())
        .whereEqualTo("tenantId", tenantId)
        .whereEqualTo("txId", txId);
    List<MatchResult> out = new ArrayList<>();
    for (DocumentSnapshot doc : execute(query)) {
      out.add(fromMatch(doc.getData()));
    }
    return out;
  }

  @Override
  public IdempotencyRecord getIdempotency(String key) {
    DocumentSnapshot doc = get(idemProps.getCollection(), key);
    if (doc == null || !doc.exists()) {
      return null;
    }
    IdempotencyRecord record = fromIdem(doc.getData());
    if (record.expiresAt() != null && record.expiresAt().isBefore(Instant.now())) {
      return null;
    }
    return record;
  }

  @Override
  public void saveIdempotency(IdempotencyRecord record) {
    set(idemProps.getCollection(), record.key(), mapIdem(record));
  }

  private void set(String collection, String id, Map<String, Object> data) {
    try {
      ApiFuture<WriteResult> future = firestore.collection(collection).document(id).set(data);
      future.get();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to persist firestore document", ex);
    }
  }

  private DocumentSnapshot get(String collection, String id) {
    try {
      return firestore.collection(collection).document(id).get().get();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to read firestore document", ex);
    }
  }

  private List<QueryDocumentSnapshot> execute(Query query) {
    try {
      QuerySnapshot qs = query.get().get();
      return qs.getDocuments();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to query firestore", ex);
    }
  }

  private Map<String, Object> mapImport(StatementImport i) {
    Map<String, Object> m = new HashMap<>();
    m.put("id", i.id());
    m.put("tenantId", i.tenantId());
    m.put("accountId", i.accountId());
    m.put("format", i.format());
    m.put("periodStart", i.periodStart() == null ? null : i.periodStart().toString());
    m.put("periodEnd", i.periodEnd() == null ? null : i.periodEnd().toString());
    m.put("createdAt", ts(i.createdAt()));
    m.put("totalTransactions", i.totalTransactions());
    m.put("totalCreditsCents", i.totalCreditsCents());
    m.put("totalDebitsCents", i.totalDebitsCents());
    return m;
  }

  private StatementImport fromImport(Map<String, Object> m) {
    return new StatementImport(
        (String) m.get("id"),
        (String) m.get("tenantId"),
        (String) m.get("accountId"),
        (String) m.get("format"),
        parseDate((String) m.get("periodStart")),
        parseDate((String) m.get("periodEnd")),
        parseInstant(m.get("createdAt")),
        ((Number) m.getOrDefault("totalTransactions", 0)).intValue(),
        ((Number) m.getOrDefault("totalCreditsCents", 0)).longValue(),
        ((Number) m.getOrDefault("totalDebitsCents", 0)).longValue()
    );
  }

  private Map<String, Object> mapTx(BankTransaction t) {
    Map<String, Object> m = new HashMap<>();
    m.put("id", t.id());
    m.put("tenantId", t.tenantId());
    m.put("importId", t.importId());
    m.put("date", t.date() == null ? null : t.date().toString());
    m.put("amountCents", t.amountCents());
    m.put("type", t.type());
    m.put("description", t.description());
    m.put("reference", t.reference());
    return m;
  }

  private BankTransaction fromTx(Map<String, Object> m) {
    return new BankTransaction(
        (String) m.get("id"),
        (String) m.get("tenantId"),
        (String) m.get("importId"),
        parseDate((String) m.get("date")),
        ((Number) m.getOrDefault("amountCents", 0)).longValue(),
        (String) m.get("type"),
        (String) m.get("description"),
        (String) m.get("reference")
    );
  }

  private Map<String, Object> mapEvent(PaymentEvent e) {
    Map<String, Object> m = new HashMap<>();
    m.put("id", e.id());
    m.put("tenantId", e.tenantId());
    m.put("paidAt", ts(e.paidAt()));
    m.put("amountCents", e.amountCents());
    m.put("reference", e.reference());
    m.put("providerPaymentId", e.providerPaymentId());
    m.put("raw", e.raw());
    return m;
  }

  @SuppressWarnings("unchecked")
  private PaymentEvent fromEvent(Map<String, Object> m) {
    return new PaymentEvent(
        (String) m.get("id"),
        (String) m.get("tenantId"),
        parseInstant(m.get("paidAt")),
        ((Number) m.getOrDefault("amountCents", 0)).longValue(),
        (String) m.get("reference"),
        (String) m.get("providerPaymentId"),
        (Map<String, Object>) m.getOrDefault("raw", Map.of())
    );
  }

  private Map<String, Object> mapMatch(MatchResult mr) {
    Map<String, Object> m = new HashMap<>();
    m.put("id", mr.id());
    m.put("tenantId", mr.tenantId());
    m.put("importId", mr.importId());
    m.put("txId", mr.txId());
    m.put("eventId", mr.eventId());
    m.put("matchedAt", ts(mr.matchedAt()));
    m.put("ruleApplied", mr.ruleApplied());
    m.put("confidence", mr.confidence());
    return m;
  }

  private MatchResult fromMatch(Map<String, Object> m) {
    return new MatchResult(
        (String) m.get("id"),
        (String) m.get("tenantId"),
        (String) m.get("importId"),
        (String) m.get("txId"),
        (String) m.get("eventId"),
        parseInstant(m.get("matchedAt")),
        (String) m.get("ruleApplied"),
        ((Number) m.getOrDefault("confidence", 0)).doubleValue()
    );
  }

  private Map<String, Object> mapIdem(IdempotencyRecord r) {
    Map<String, Object> m = new HashMap<>();
    m.put("key", r.key());
    m.put("statusCode", r.statusCode());
    m.put("bodyJson", r.bodyJson());
    m.put("createdAt", ts(r.createdAt()));
    m.put("expiresAt", ts(r.expiresAt()));
    return m;
  }

  private IdempotencyRecord fromIdem(Map<String, Object> m) {
    return new IdempotencyRecord(
        (String) m.get("key"),
        ((Number) m.getOrDefault("statusCode", 200)).intValue(),
        (String) m.getOrDefault("bodyJson", "{}"),
        parseInstant(m.get("createdAt")),
        parseInstant(m.get("expiresAt"))
    );
  }

  private Timestamp ts(Instant instant) {
    if (instant == null) {
      return null;
    }
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
  }

  private Instant parseInstant(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Timestamp t) {
      return t.toDate().toInstant();
    }
    if (value instanceof String s && !s.isBlank()) {
      return Instant.parse(s);
    }
    return null;
  }

  private LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return LocalDate.parse(value);
  }
}
