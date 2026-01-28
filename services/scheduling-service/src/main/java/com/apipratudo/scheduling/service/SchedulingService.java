package com.apipratudo.scheduling.service;

import com.apipratudo.scheduling.client.WebhookEventClient;
import com.apipratudo.scheduling.client.WebhookEventData;
import com.apipratudo.scheduling.client.WebhookEventRequest;
import com.apipratudo.scheduling.config.SchedulingProperties;
import com.apipratudo.scheduling.dto.AgendaResponse;
import com.apipratudo.scheduling.dto.AppointmentResponse;
import com.apipratudo.scheduling.dto.CancelResponse;
import com.apipratudo.scheduling.dto.ConfirmResponse;
import com.apipratudo.scheduling.dto.CustomerRequest;
import com.apipratudo.scheduling.dto.NotifyResponse;
import com.apipratudo.scheduling.dto.ReservationRequest;
import com.apipratudo.scheduling.dto.ReservationResponse;
import com.apipratudo.scheduling.dto.ServiceCreateRequest;
import com.apipratudo.scheduling.dto.ServiceResponse;
import com.apipratudo.scheduling.dto.SlotResponse;
import com.apipratudo.scheduling.dto.SlotsResponse;
import com.apipratudo.scheduling.error.ApiException;
import com.apipratudo.scheduling.error.HoldExpiredException;
import com.apipratudo.scheduling.error.ResourceNotFoundException;
import com.apipratudo.scheduling.error.SlotUnavailableException;
import com.apipratudo.scheduling.idempotency.IdempotencyTransaction;
import com.apipratudo.scheduling.model.Appointment;
import com.apipratudo.scheduling.model.AppointmentStatus;
import com.apipratudo.scheduling.model.Customer;
import com.apipratudo.scheduling.model.ServiceDef;
import com.apipratudo.scheduling.model.Slot;
import com.apipratudo.scheduling.repository.SchedulingStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SchedulingService {

  private final SchedulingStore store;
  private final SchedulingProperties properties;
  private final Clock clock;
  private final WebhookEventClient webhookEventClient;

  public SchedulingService(
      SchedulingStore store,
      SchedulingProperties properties,
      Clock clock,
      WebhookEventClient webhookEventClient
  ) {
    this.store = store;
    this.properties = properties;
    this.clock = clock;
    this.webhookEventClient = webhookEventClient;
  }

  public ServiceDef createService(String tenantId, ServiceCreateRequest request, IdempotencyTransaction tx) {
    ServiceDef service = new ServiceDef(
        "srv_" + UUID.randomUUID(),
        tenantId,
        request.name(),
        request.durationMin(),
        request.prepMin(),
        request.bufferMin(),
        request.noShowFeeCents(),
        request.active(),
        Instant.now(clock)
    );
    return store.saveService(tenantId, service, tx);
  }

  public List<ServiceDef> listServices(String tenantId) {
    return store.listServices(tenantId).stream()
        .sorted(Comparator.comparing(ServiceDef::createdAt))
        .collect(Collectors.toList());
  }

  public SlotsResponse availableSlots(
      String tenantId,
      String serviceId,
      LocalDate date,
      String agendaId,
      String zoneId
  ) {
    ServiceDef service = store.findServiceById(tenantId, serviceId)
        .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));

    ZoneId zone = resolveZone(zoneId);
    ZonedDateTime dayStart = ZonedDateTime.of(date, LocalTime.parse(properties.getDayStart()), zone);
    ZonedDateTime dayEnd = ZonedDateTime.of(date, LocalTime.parse(properties.getDayEnd()), zone);

    int totalDurationMin = service.durationMin() + service.prepMin() + service.bufferMin();
    List<Appointment> appointments = store.listAppointments(
        tenantId,
        agendaId,
        dayStart.toInstant(),
        dayEnd.toInstant()
    );

    List<SlotResponse> slots = new ArrayList<>();
    ZonedDateTime cursor = dayStart;
    Instant now = Instant.now(clock);

    while (!cursor.plusMinutes(totalDurationMin).isAfter(dayEnd)) {
      Instant startAt = cursor.toInstant();
      Instant endAt = cursor.plusMinutes(totalDurationMin).toInstant();
      if (!conflicts(startAt, endAt, appointments, now)) {
        slots.add(new SlotResponse(startAt, endAt));
      }
      cursor = cursor.plusMinutes(properties.getSlotStepMin());
    }

    return new SlotsResponse(serviceId, date.toString(), agendaId, properties.getSlotStepMin(), slots);
  }

  public ReservationResponse reserve(String tenantId, ReservationRequest request, IdempotencyTransaction tx) {
    ServiceDef service = store.findServiceById(tenantId, request.serviceId())
        .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + request.serviceId()));

    Instant startAt = request.startAt();
    int totalDurationMin = service.durationMin() + service.prepMin() + service.bufferMin();
    Instant endAt = startAt.plus(Duration.ofMinutes(totalDurationMin));

    ZoneId zone = resolveZone(properties.getTimezone());
    LocalDate date = LocalDate.ofInstant(startAt, zone);
    ZonedDateTime dayStart = ZonedDateTime.of(date, LocalTime.parse(properties.getDayStart()), zone);
    ZonedDateTime dayEnd = ZonedDateTime.of(date, LocalTime.parse(properties.getDayEnd()), zone);

    List<Appointment> appointments = store.listAppointments(
        tenantId,
        request.agendaId(),
        dayStart.toInstant(),
        dayEnd.toInstant()
    );

    Instant now = Instant.now(clock);
    if (conflicts(startAt, endAt, appointments, now)) {
      throw new SlotUnavailableException();
    }

    Instant holdExpiresAt = now.plus(Duration.ofMinutes(properties.getHoldTtlMin()));
    Appointment appointment = new Appointment(
        "apt_" + UUID.randomUUID(),
        tenantId,
        service.id(),
        request.agendaId(),
        startAt,
        endAt,
        AppointmentStatus.HELD,
        holdExpiresAt,
        toCustomer(request.customer()),
        request.notes(),
        service.noShowFeeCents(),
        now,
        now,
        null,
        null
    );

    Appointment saved = store.saveAppointment(tenantId, appointment, tx);
    return new ReservationResponse(saved.id(), saved.status().name(), saved.holdExpiresAt(), saved.startAt(),
        saved.endAt());
  }

  public ConfirmResponse confirm(String tenantId, String appointmentId) {
    Appointment appointment = store.findAppointment(tenantId, appointmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

    Instant now = Instant.now(clock);
    if (appointment.status() == AppointmentStatus.CANCELLED) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "Appointment is cancelled");
    }
    if (appointment.status() == AppointmentStatus.CONFIRMED) {
      return new ConfirmResponse(appointment.id(), appointment.status().name());
    }
    if (appointment.holdExpiresAt() != null && now.isAfter(appointment.holdExpiresAt())) {
      throw new HoldExpiredException();
    }

    Appointment updated = appointment.withStatus(AppointmentStatus.CONFIRMED, now);
    store.saveAppointment(tenantId, updated);
    return new ConfirmResponse(updated.id(), updated.status().name());
  }

  public CancelResponse cancel(String tenantId, String appointmentId) {
    Appointment appointment = store.findAppointment(tenantId, appointmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

    Instant now = Instant.now(clock);
    long fee = calculateCancellationFee(appointment, now);

    Appointment updated = appointment.withStatus(AppointmentStatus.CANCELLED, now);
    store.saveAppointment(tenantId, updated);
    return new CancelResponse(updated.id(), updated.status().name(), fee);
  }

  public NotifyResponse notify(String tenantId, String appointmentId, String type, String traceId) {
    Appointment appointment = store.findAppointment(tenantId, appointmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

    Instant now = Instant.now(clock);
    Appointment updated = appointment.withNotification(type, now, now);
    store.saveAppointment(tenantId, updated);

    WebhookEventRequest event = new WebhookEventRequest(
        "SCHEDULING_REMINDER",
        tenantId,
        new WebhookEventData(appointmentId, type, now),
        now
    );
    webhookEventClient.publishEvent(event, traceId);

    return new NotifyResponse(true, appointmentId, type);
  }

  public AgendaResponse agenda(String tenantId, String agendaId, Instant from, Instant to) {
    List<Appointment> appointments = store.listAppointments(tenantId, agendaId, from, to);
    List<AppointmentResponse> responses = appointments.stream()
        .sorted(Comparator.comparing(Appointment::startAt))
        .map(this::toResponse)
        .collect(Collectors.toList());

    return new AgendaResponse(agendaId, from.toString(), to.toString(), responses);
  }

  private AppointmentResponse toResponse(Appointment appointment) {
    CustomerRequest customer = null;
    if (appointment.customer() != null) {
      customer = new CustomerRequest(
          appointment.customer().name(),
          appointment.customer().phone(),
          appointment.customer().email()
      );
    }
    return new AppointmentResponse(
        appointment.id(),
        appointment.serviceId(),
        appointment.agendaId(),
        appointment.status().name(),
        appointment.startAt(),
        appointment.endAt(),
        appointment.holdExpiresAt(),
        customer,
        appointment.notes()
    );
  }

  private boolean conflicts(Instant startAt, Instant endAt, List<Appointment> appointments, Instant now) {
    for (Appointment appointment : appointments) {
      if (appointment.status() == AppointmentStatus.CANCELLED) {
        continue;
      }
      if (appointment.status() == AppointmentStatus.HELD
          && appointment.holdExpiresAt() != null
          && now.isAfter(appointment.holdExpiresAt())) {
        continue;
      }
      if (overlaps(startAt, endAt, appointment.startAt(), appointment.endAt())) {
        return true;
      }
    }
    return false;
  }

  private boolean overlaps(Instant startAt, Instant endAt, Instant existingStart, Instant existingEnd) {
    return startAt.isBefore(existingEnd) && endAt.isAfter(existingStart);
  }

  private long calculateCancellationFee(Appointment appointment, Instant now) {
    long noShowFee = appointment.noShowFeeCents();
    if (noShowFee <= 0) {
      return 0;
    }
    Duration until = Duration.between(now, appointment.startAt());
    if (until.toMinutes() < 60) {
      return noShowFee / 2;
    }
    return 0;
  }

  private Customer toCustomer(CustomerRequest request) {
    if (request == null) {
      return null;
    }
    return new Customer(request.name(), request.phone(), request.email());
  }

  private ZoneId resolveZone(String zoneId) {
    String resolved = zoneId;
    if (resolved == null || resolved.isBlank()) {
      resolved = properties.getTimezone();
    }
    return ZoneId.of(resolved);
  }

  public ServiceResponse toResponse(ServiceDef service) {
    return new ServiceResponse(
        service.id(),
        service.name(),
        service.durationMin(),
        service.prepMin(),
        service.bufferMin(),
        service.noShowFeeCents(),
        service.active(),
        service.createdAt()
    );
  }
}
