package com.apipratudo.helpdesk.config;

import com.apipratudo.helpdesk.repository.FirestoreHelpdeskStore;
import com.apipratudo.helpdesk.repository.HelpdeskStore;
import com.apipratudo.helpdesk.repository.InMemoryHelpdeskStore;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HelpdeskStoreConfig {

  @Bean
  @ConditionalOnBean(Firestore.class)
  public HelpdeskStore firestoreHelpdeskStore(
      Firestore firestore,
      HelpdeskStorageProperties storageProperties,
      WhatsappProperties whatsappProperties,
      IdempotencyProperties idempotencyProperties
  ) {
    return new FirestoreHelpdeskStore(firestore, storageProperties, whatsappProperties, idempotencyProperties);
  }

  @Bean
  @ConditionalOnMissingBean(Firestore.class)
  public HelpdeskStore inMemoryHelpdeskStore() {
    return new InMemoryHelpdeskStore();
  }
}
