package com.apipratudo.billing.config;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public class FirestoreAvailableCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    Environment env = context.getEnvironment();
    String enabled = env.getProperty("app.firestore.enabled");
    if (StringUtils.hasText(enabled) && enabled.equalsIgnoreCase("false")) {
      return false;
    }

    String emulatorHost = env.getProperty("app.firestore.emulator-host");
    if (!StringUtils.hasText(emulatorHost)) {
      emulatorHost = System.getenv("FIRESTORE_EMULATOR_HOST");
    }
    if (StringUtils.hasText(emulatorHost)) {
      return true;
    }

    try {
      GoogleCredentials.getApplicationDefault();
      return true;
    } catch (IOException ex) {
      return false;
    }
  }
}
