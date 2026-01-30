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
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SchedulingStore {

  ServiceDef saveService(String tenantId, ServiceDef service);

  ServiceDef saveService(String tenantId, ServiceDef service, IdempotencyTransaction transaction);

  List<ServiceDef> listServices(String tenantId);

  Optional<ServiceDef> findServiceById(String tenantId, String serviceId);

  Appointment saveAppointment(String tenantId, Appointment appointment);

  Appointment saveAppointment(String tenantId, Appointment appointment, IdempotencyTransaction transaction);

  Optional<Appointment> findAppointment(String tenantId, String appointmentId);

  List<Appointment> listAppointments(String tenantId, String agendaId, Instant from, Instant to);

  List<Appointment> listAppointmentsByStatusBefore(String tenantId, AppointmentStatus status, Instant before, int limit);

  Agenda saveAgenda(String tenantId, Agenda agenda);

  Agenda saveAgenda(String tenantId, Agenda agenda, IdempotencyTransaction transaction);

  Optional<Agenda> findAgenda(String tenantId, String agendaId);

  List<Agenda> listAgendas(String tenantId);

  AgendaCredits saveAgendaCredits(String tenantId, AgendaCredits credits);

  Optional<AgendaCredits> findAgendaCredits(String tenantId, String agendaId);

  AgendaCreditCharge saveAgendaCreditCharge(String tenantId, AgendaCreditCharge charge);

  Optional<AgendaCreditCharge> findAgendaCreditCharge(String tenantId, String chargeId);

  List<Fine> listFines(String tenantId, String agendaId, FineStatus status);

  Fine saveFine(String tenantId, Fine fine);

  Optional<Fine> findFine(String tenantId, String fineId);

  void ensureTenant(String tenantId);

  Set<String> listTenants();
}
