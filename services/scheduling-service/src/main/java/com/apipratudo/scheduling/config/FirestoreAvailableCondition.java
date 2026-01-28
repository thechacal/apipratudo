package com.apipratudo.scheduling.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class FirestoreAvailableCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String enabled = context.getEnvironment().getProperty("app.firestore.enabled", "true");
    return Boolean.parseBoolean(enabled);
  }
}
