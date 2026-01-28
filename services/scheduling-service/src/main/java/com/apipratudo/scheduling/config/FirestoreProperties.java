package com.apipratudo.scheduling.config;

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
    private String services = "scheduling_services";
    private String appointments = "scheduling_appointments";
    private String idempotency = "scheduling_idempotency";

    public String getServices() {
      return services;
    }

    public void setServices(String services) {
      this.services = services;
    }

    public String getAppointments() {
      return appointments;
    }

    public void setAppointments(String appointments) {
      this.appointments = appointments;
    }

    public String getIdempotency() {
      return idempotency;
    }

    public void setIdempotency(String idempotency) {
      this.idempotency = idempotency;
    }
  }
}
