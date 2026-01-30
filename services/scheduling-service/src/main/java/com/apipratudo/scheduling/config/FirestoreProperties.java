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
    private String agendas = "scheduling_agendas";
    private String agendaCredits = "scheduling_agenda_credits";
    private String agendaCharges = "scheduling_agenda_charges";
    private String fines = "scheduling_fines";

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

    public String getAgendas() {
      return agendas;
    }

    public void setAgendas(String agendas) {
      this.agendas = agendas;
    }

    public String getAgendaCredits() {
      return agendaCredits;
    }

    public void setAgendaCredits(String agendaCredits) {
      this.agendaCredits = agendaCredits;
    }

    public String getAgendaCharges() {
      return agendaCharges;
    }

    public void setAgendaCharges(String agendaCharges) {
      this.agendaCharges = agendaCharges;
    }

    public String getFines() {
      return fines;
    }

    public void setFines(String fines) {
      this.fines = fines;
    }
  }
}
