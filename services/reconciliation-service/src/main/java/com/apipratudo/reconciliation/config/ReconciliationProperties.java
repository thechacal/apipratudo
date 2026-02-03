package com.apipratudo.reconciliation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reconciliation")
public class ReconciliationProperties {

  private String importsCollection = "reconciliation_imports";
  private String transactionsCollection = "reconciliation_transactions";
  private String eventsCollection = "reconciliation_payment_events";
  private String matchesCollection = "reconciliation_matches";
  private String defaultAccountId = "main";

  public String getImportsCollection() {
    return importsCollection;
  }

  public void setImportsCollection(String importsCollection) {
    this.importsCollection = importsCollection;
  }

  public String getTransactionsCollection() {
    return transactionsCollection;
  }

  public void setTransactionsCollection(String transactionsCollection) {
    this.transactionsCollection = transactionsCollection;
  }

  public String getEventsCollection() {
    return eventsCollection;
  }

  public void setEventsCollection(String eventsCollection) {
    this.eventsCollection = eventsCollection;
  }

  public String getMatchesCollection() {
    return matchesCollection;
  }

  public void setMatchesCollection(String matchesCollection) {
    this.matchesCollection = matchesCollection;
  }

  public String getDefaultAccountId() {
    return defaultAccountId;
  }

  public void setDefaultAccountId(String defaultAccountId) {
    this.defaultAccountId = defaultAccountId;
  }
}
