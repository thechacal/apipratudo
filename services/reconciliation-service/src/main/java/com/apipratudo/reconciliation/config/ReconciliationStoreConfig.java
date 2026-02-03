package com.apipratudo.reconciliation.config;

import com.apipratudo.reconciliation.repository.FirestoreReconciliationStore;
import com.apipratudo.reconciliation.repository.InMemoryReconciliationStore;
import com.apipratudo.reconciliation.repository.ReconciliationStore;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReconciliationStoreConfig {

  @Bean
  @ConditionalOnBean(Firestore.class)
  public ReconciliationStore firestoreReconciliationStore(
      Firestore firestore,
      ReconciliationProperties properties,
      IdempotencyProperties idempotencyProperties
  ) {
    return new FirestoreReconciliationStore(firestore, properties, idempotencyProperties);
  }

  @Bean
  @ConditionalOnMissingBean(Firestore.class)
  public ReconciliationStore inMemoryReconciliationStore() {
    return new InMemoryReconciliationStore();
  }
}
