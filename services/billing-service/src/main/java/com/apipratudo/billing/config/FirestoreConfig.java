package com.apipratudo.billing.config;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class FirestoreConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  @Conditional(FirestoreAvailableCondition.class)
  public Firestore firestore(FirestoreProperties properties) {
    String projectId = resolveProjectId(properties);
    String emulatorHost = resolveEmulatorHost(properties);
    FirestoreOptions.Builder builder = FirestoreOptions.getDefaultInstance().toBuilder();
    if (StringUtils.hasText(projectId)) {
      builder.setProjectId(projectId);
    }
    if (StringUtils.hasText(emulatorHost)) {
      builder.setEmulatorHost(emulatorHost);
    }
    return builder.build().getService();
  }

  private String resolveProjectId(FirestoreProperties properties) {
    if (StringUtils.hasText(properties.getProjectId())) {
      return properties.getProjectId();
    }
    return System.getenv("GOOGLE_CLOUD_PROJECT");
  }

  private String resolveEmulatorHost(FirestoreProperties properties) {
    if (StringUtils.hasText(properties.getEmulatorHost())) {
      return properties.getEmulatorHost();
    }
    return System.getenv("FIRESTORE_EMULATOR_HOST");
  }
}
