package com.apipratudo.reconciliation.config;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties({FirestoreProperties.class, SecurityProperties.class, IdempotencyProperties.class,
    ReconciliationProperties.class})
public class FirestoreConfig {

  @Bean
  @Conditional(FirestoreAvailableCondition.class)
  @ConditionalOnMissingBean
  public Firestore firestore(FirestoreProperties properties) {
    FirestoreOptions.Builder builder = FirestoreOptions.newBuilder().setProjectId(properties.getProjectId());
    if (StringUtils.hasText(properties.getEmulatorHost())) {
      builder.setEmulatorHost(properties.getEmulatorHost());
    }
    return builder.build().getService();
  }
}
