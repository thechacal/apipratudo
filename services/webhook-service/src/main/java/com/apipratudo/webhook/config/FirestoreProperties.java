package com.apipratudo.webhook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.firestore")
public class FirestoreProperties {

  private boolean enabled = true;
  private String projectId;
  private String emulatorHost;
  private final Collections collections = new Collections();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getEmulatorHost() {
    return emulatorHost;
  }

  public void setEmulatorHost(String emulatorHost) {
    this.emulatorHost = emulatorHost;
  }

  public Collections getCollections() {
    return collections;
  }

  public static class Collections {
    private String webhooks = "webhooks";

    public String getWebhooks() {
      return webhooks;
    }

    public void setWebhooks(String webhooks) {
      this.webhooks = webhooks;
    }
  }
}
