package com.apipratudo.quota.config;

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

    private String apiKeys = "api_keys";
    private String quotaWindows = "quota_windows";
    private String idempotencyQuota = "idempotency_quota";
    private String keyCreationLimits = "key_creation_limits";

    public String getApiKeys() {
      return apiKeys;
    }

    public void setApiKeys(String apiKeys) {
      this.apiKeys = apiKeys;
    }

    public String getQuotaWindows() {
      return quotaWindows;
    }

    public void setQuotaWindows(String quotaWindows) {
      this.quotaWindows = quotaWindows;
    }

    public String getIdempotencyQuota() {
      return idempotencyQuota;
    }

    public void setIdempotencyQuota(String idempotencyQuota) {
      this.idempotencyQuota = idempotencyQuota;
    }

    public String getKeyCreationLimits() {
      return keyCreationLimits;
    }

    public void setKeyCreationLimits(String keyCreationLimits) {
      this.keyCreationLimits = keyCreationLimits;
    }
  }
}
