package com.apipratudo.scheduling.repository;

import com.apipratudo.scheduling.idempotency.IdempotencyTransaction;
import com.apipratudo.scheduling.model.Agenda;
import com.apipratudo.scheduling.model.AgendaCreditCharge;
import com.apipratudo.scheduling.model.AgendaCredits;
import com.apipratudo.scheduling.model.Appointment;
import com.apipratudo.scheduling.model.AppointmentStatus;
import com.apipratudo.scheduling.model.Fine;
import com.apipratudo.scheduling.model.FineStatus;
import com.apipratudo.scheduling.model.ServiceDef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(Firestore.class)
public class InMemorySchedulingStore implements SchedulingStore {

  private final Map<String, Map<String, ServiceDef>> services = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Appointment>> appointments = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Agenda>> agendas = new ConcurrentHashMap<>();
  private final Map<String, Map<String, AgendaCredits>> agendaCredits = new ConcurrentHashMap<>();
  private final Map<String, Map<String, AgendaCreditCharge>> agendaCharges = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Fine>> fines = new ConcurrentHashMap<>();
  private final Set<String> tenants = ConcurrentHashMap.newKeySet();

  @Override
  public ServiceDef saveService(String tenantId, ServiceDef service) {
    ensureTenant(tenantId);
    services.computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>())
        .put(service.id(), service);
    return service;
  }

  @Override
  public ServiceDef saveService(String tenantId, ServiceDef service, IdempotencyTransaction transaction) {
    return saveService(tenantId, service);
  }

  @Override
  public List<ServiceDef> listServices(String tenantId) {
    return new ArrayList<>(services.getOrDefault(tenantId, Collections.emptyMap()).values());
  }

  @Override
  public Optional<ServiceDef> findServiceById(String tenantId, String serviceId) {
    return Optional.ofNullable(services.getOrDefault(tenantId, Collections.emptyMap()).get(serviceId));
  }

  @Override
  public Appointment saveAppointment(String tenantId, Appointment appointment) {
    ensureTenant(tenantId);
    appointments.computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>())
        .put(appointment.id(), appointment);
    return appointment;
  }

  @Override
  public Appointment saveAppointment(String tenantId, Appointment appointment, IdempotencyTransaction transaction) {
    return saveAppointment(tenantId, appointment);
  }

  @Override
  public Optional<Appointment> findAppointment(String tenantId, String appointmentId) {
    return Optional.ofNullable(appointments.getOrDefault(tenantId, Collections.emptyMap()).get(appointmentId));
  }

  @Override
  public List<Appointment> listAppointments(String tenantId, String agendaId, Instant from, Instant to) {
    Map<String, Appointment> tenantAppointments = appointments.getOrDefault(tenantId, Collections.emptyMap());
    return tenantAppointments.values().stream()
        .filter(appointment -> appointment.agendaId().equals(agendaId))
        .filter(appointment -> !appointment.startAt().isBefore(from) && !appointment.startAt().isAfter(to))
        .collect(Collectors.toList());
  }

  @Override
  public List<Appointment> listAppointmentsByStatusBefore(String tenantId, AppointmentStatus status, Instant before,
                                                          int limit) {
    Map<String, Appointment> tenantAppointments = appointments.getOrDefault(tenantId, Collections.emptyMap());
    return tenantAppointments.values().stream()
        .filter(appointment -> appointment.status() == status)
        .filter(appointment -> !appointment.startAt().isAfter(before))
        .limit(limit)
        .collect(Collectors.toList());
  }

  @Override
  public Agenda saveAgenda(String tenantId, Agenda agenda) {
    ensureTenant(tenantId);
    agendas.computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>())
        .put(agenda.id(), agenda);
    return agenda;
  }

  @Override
  public Agenda saveAgenda(String tenantId, Agenda agenda, IdempotencyTransaction transaction) {
    return saveAgenda(tenantId, agenda);
  }

  @Override
  public Optional<Agenda> findAgenda(String tenantId, String agendaId) {
    return Optional.ofNullable(agendas.getOrDefault(tenantId, Collections.emptyMap()).get(agendaId));
  }

  @Override
  public List<Agenda> listAgendas(String tenantId) {
    return new ArrayList<>(agendas.getOrDefault(tenantId, Collections.emptyMap()).values());
  }

  @Override
  public AgendaCredits saveAgendaCredits(String tenantId, AgendaCredits credits) {
    ensureTenant(tenantId);
    agendaCredits.computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>())
        .put(credits.agendaId(), credits);
    return credits;
  }

  @Override
  public Optional<AgendaCredits> findAgendaCredits(String tenantId, String agendaId) {
    return Optional.ofNullable(agendaCredits.getOrDefault(tenantId, Collections.emptyMap()).get(agendaId));
  }

  @Override
  public AgendaCreditCharge saveAgendaCreditCharge(String tenantId, AgendaCreditCharge charge) {
    ensureTenant(tenantId);
    agendaCharges.computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>())
        .put(charge.chargeId(), charge);
    return charge;
  }

  @Override
  public Optional<AgendaCreditCharge> findAgendaCreditCharge(String tenantId, String chargeId) {
    return Optional.ofNullable(agendaCharges.getOrDefault(tenantId, Collections.emptyMap()).get(chargeId));
  }

  @Override
  public List<Fine> listFines(String tenantId, String agendaId, FineStatus status) {
    Map<String, Fine> tenantFines = fines.getOrDefault(tenantId, Collections.emptyMap());
    return tenantFines.values().stream()
        .filter(fine -> agendaId == null || fine.agendaId().equals(agendaId))
        .filter(fine -> status == null || fine.status() == status)
        .collect(Collectors.toList());
  }

  @Override
  public Fine saveFine(String tenantId, Fine fine) {
    ensureTenant(tenantId);
    fines.computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>())
        .put(fine.id(), fine);
    return fine;
  }

  @Override
  public Optional<Fine> findFine(String tenantId, String fineId) {
    return Optional.ofNullable(fines.getOrDefault(tenantId, Collections.emptyMap()).get(fineId));
  }

  @Override
  public void ensureTenant(String tenantId) {
    if (tenantId != null) {
      tenants.add(tenantId);
    }
  }

  @Override
  public Set<String> listTenants() {
    return Set.copyOf(tenants);
  }
}
