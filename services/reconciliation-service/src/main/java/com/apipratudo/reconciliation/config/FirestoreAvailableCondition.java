package com.apipratudo.reconciliation.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public class FirestoreAvailableCondition extends SpringBootCondition {

  @Override
  public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String enabledValue = context.getEnvironment().getProperty("app.firestore.enabled",
        context.getEnvironment().getProperty("APP_FIRESTORE_ENABLED", "true"));
    boolean enabled = Boolean.parseBoolean(enabledValue);
    String projectId = context.getEnvironment().getProperty("app.firestore.project-id",
        context.getEnvironment().getProperty("APP_FIRESTORE_PROJECT_ID",
            context.getEnvironment().getProperty("GOOGLE_CLOUD_PROJECT", "")));
    if (enabled && StringUtils.hasText(projectId)) {
      return ConditionOutcome.match("Firestore enabled and projectId present");
    }
    return ConditionOutcome.noMatch("Firestore disabled or projectId missing");
  }
}
