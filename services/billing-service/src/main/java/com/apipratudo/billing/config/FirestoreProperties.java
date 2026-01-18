package com.apipratudo.billing.config;

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
    private String charges = "billing_charges";
    private String webhookEvents = "billing_webhook_events";
    private String webhookInvalid = "billing_webhook_invalid";
    private String webhookForm = "billing_webhook_form";

    public String getCharges() {
      return charges;
    }

    public void setCharges(String charges) {
      this.charges = charges;
    }

    public String getWebhookEvents() {
      return webhookEvents;
    }

    public void setWebhookEvents(String webhookEvents) {
      this.webhookEvents = webhookEvents;
    }

    public String getWebhookInvalid() {
      return webhookInvalid;
    }

    public void setWebhookInvalid(String webhookInvalid) {
      this.webhookInvalid = webhookInvalid;
    }

    public String getWebhookForm() {
      return webhookForm;
    }

    public void setWebhookForm(String webhookForm) {
      this.webhookForm = webhookForm;
    }
  }
}
