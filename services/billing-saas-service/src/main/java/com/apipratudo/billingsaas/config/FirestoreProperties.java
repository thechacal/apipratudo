package com.apipratudo.billingsaas.config;

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
    private String customers = "billing_saas_customers";
    private String charges = "billing_saas_charges";
    private String idempotency = "billing_saas_idempotency";
    private String pixProviderIndex = "pix_provider_index";

    public String getCustomers() {
      return customers;
    }

    public void setCustomers(String customers) {
      this.customers = customers;
    }

    public String getCharges() {
      return charges;
    }

    public void setCharges(String charges) {
      this.charges = charges;
    }

    public String getIdempotency() {
      return idempotency;
    }

    public void setIdempotency(String idempotency) {
      this.idempotency = idempotency;
    }

    public String getPixProviderIndex() {
      return pixProviderIndex;
    }

    public void setPixProviderIndex(String pixProviderIndex) {
      this.pixProviderIndex = pixProviderIndex;
    }
  }
}
