package com.apipratudo.scheduling.repository;

import com.apipratudo.scheduling.idempotency.IdempotencyTransaction;
import com.apipratudo.scheduling.model.Appointment;
import com.apipratudo.scheduling.model.ServiceDef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  @Override
  public ServiceDef saveService(String tenantId, ServiceDef service) {
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
}
