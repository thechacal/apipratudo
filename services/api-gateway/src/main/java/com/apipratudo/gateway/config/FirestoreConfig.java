package com.apipratudo.gateway.config;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class FirestoreConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true", matchIfMissing = true)
  public Firestore firestore() {
    String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
    FirestoreOptions.Builder builder = FirestoreOptions.getDefaultInstance().toBuilder();
    if (StringUtils.hasText(projectId)) {
      builder.setProjectId(projectId);
    }
    return builder.build().getService();
  }
}
