package com.apipratudo.scheduling.repository;

import com.apipratudo.scheduling.idempotency.IdempotencyTransaction;
import com.apipratudo.scheduling.model.Appointment;
import com.apipratudo.scheduling.model.ServiceDef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SchedulingStore {

  ServiceDef saveService(String tenantId, ServiceDef service);

  ServiceDef saveService(String tenantId, ServiceDef service, IdempotencyTransaction transaction);

  List<ServiceDef> listServices(String tenantId);

  Optional<ServiceDef> findServiceById(String tenantId, String serviceId);

  Appointment saveAppointment(String tenantId, Appointment appointment);

  Appointment saveAppointment(String tenantId, Appointment appointment, IdempotencyTransaction transaction);

  Optional<Appointment> findAppointment(String tenantId, String appointmentId);

  List<Appointment> listAppointments(String tenantId, String agendaId, Instant from, Instant to);
}
