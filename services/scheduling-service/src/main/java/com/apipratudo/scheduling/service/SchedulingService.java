package com.apipratudo.scheduling.service;

import com.apipratudo.scheduling.client.BillingChargeCreateRequest;
import com.apipratudo.scheduling.client.BillingChargeResponse;
import com.apipratudo.scheduling.client.BillingChargeStatusResponse;
import com.apipratudo.scheduling.client.BillingCustomerCreateRequest;
import com.apipratudo.scheduling.client.BillingCustomerResponse;
import com.apipratudo.scheduling.client.BillingPixGenerateRequest;
import com.apipratudo.scheduling.client.BillingPixGenerateResponse;
import com.apipratudo.scheduling.client.BillingSaasClient;
import com.apipratudo.scheduling.client.WebhookEventClient;
import com.apipratudo.scheduling.client.WebhookEventData;
import com.apipratudo.scheduling.client.WebhookEventRequest;
import com.apipratudo.scheduling.config.AgendaCreditsProperties;
import com.apipratudo.scheduling.config.NoShowProperties;
import com.apipratudo.scheduling.config.SchedulingProperties;
import com.apipratudo.scheduling.dto.AgendaCreateRequest;
import com.apipratudo.scheduling.dto.AgendaCreditsStatusResponse;
import com.apipratudo.scheduling.dto.AgendaCreditsUpgradeRequest;
import com.apipratudo.scheduling.dto.AgendaCreditsUpgradeResponse;
import com.apipratudo.scheduling.dto.AgendaInfoResponse;
import com.apipratudo.scheduling.dto.AgendaResponse;
import com.apipratudo.scheduling.dto.AppointmentResponse;
import com.apipratudo.scheduling.dto.AttendedResponse;
import com.apipratudo.scheduling.dto.CancelResponse;
import com.apipratudo.scheduling.dto.ConfirmResponse;
import com.apipratudo.scheduling.dto.CustomerRequest;
import com.apipratudo.scheduling.dto.FineResponse;
import com.apipratudo.scheduling.dto.NotifyResponse;
import com.apipratudo.scheduling.dto.ReservationRequest;
import com.apipratudo.scheduling.dto.ReservationResponse;
import com.apipratudo.scheduling.dto.ServiceCreateRequest;
import com.apipratudo.scheduling.dto.ServiceResponse;
import com.apipratudo.scheduling.dto.SlotResponse;
import com.apipratudo.scheduling.dto.SlotsResponse;
import com.apipratudo.scheduling.dto.AgendaUpdateRequest;
import com.apipratudo.scheduling.error.ApiException;
import com.apipratudo.scheduling.error.AgendaCreditsExceededException;
import com.apipratudo.scheduling.error.HoldExpiredException;
import com.apipratudo.scheduling.error.ResourceNotFoundException;
import com.apipratudo.scheduling.error.SlotUnavailableException;
import com.apipratudo.scheduling.idempotency.IdempotencyTransaction;
import com.apipratudo.scheduling.model.Agenda;
import com.apipratudo.scheduling.model.AgendaCreditCharge;
import com.apipratudo.scheduling.model.AgendaCredits;
import com.apipratudo.scheduling.model.Appointment;
import com.apipratudo.scheduling.model.AppointmentStatus;
import com.apipratudo.scheduling.model.Customer;
import com.apipratudo.scheduling.model.Fine;
import com.apipratudo.scheduling.model.FineStatus;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SchedulingService {

  private final SchedulingStore store;
  private final SchedulingProperties properties;
  private final AgendaCreditsProperties creditsProperties;
  private final NoShowProperties noShowProperties;
  private final Clock clock;
  private final WebhookEventClient webhookEventClient;
  private final BillingSaasClient billingSaasClient;

  public SchedulingService(
      SchedulingStore store,
      SchedulingProperties properties,
      AgendaCreditsProperties creditsProperties,
      NoShowProperties noShowProperties,
      Clock clock,
      WebhookEventClient webhookEventClient,
      BillingSaasClient billingSaasClient
  ) {
    this.store = store;
    this.properties = properties;
    this.creditsProperties = creditsProperties;
    this.noShowProperties = noShowProperties;
    this.clock = clock;
    this.webhookEventClient = webhookEventClient;
    this.billingSaasClient = billingSaasClient;
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

  public Agenda createAgenda(String tenantId, AgendaCreateRequest request, IdempotencyTransaction tx) {
    Instant now = Instant.now(clock);
    Agenda agenda = new Agenda(
        "agd_" + UUID.randomUUID(),
        tenantId,
        request.name(),
        request.timezone(),
        request.workingHoursStart(),
        request.workingHoursEnd(),
        request.slotStepMin(),
        request.noShowFeeCents(),
        request.active(),
        now,
        now
    );
    return store.saveAgenda(tenantId, agenda, tx);
  }

  public List<Agenda> listAgendas(String tenantId) {
    return store.listAgendas(tenantId).stream()
        .sorted(Comparator.comparing(Agenda::createdAt))
        .collect(Collectors.toList());
  }

  public Agenda getAgenda(String tenantId, String agendaId) {
    return store.findAgenda(tenantId, agendaId)
        .orElseThrow(() -> new ResourceNotFoundException("Agenda not found: " + agendaId));
  }

  public Agenda updateAgenda(String tenantId, String agendaId, AgendaUpdateRequest request, IdempotencyTransaction tx) {
    Agenda agenda = getAgenda(tenantId, agendaId);
    Instant now = Instant.now(clock);
    Agenda updated = agenda.withUpdates(
        request.name(),
        request.timezone(),
        request.workingHoursStart(),
        request.workingHoursEnd(),
        request.slotStepMin(),
        request.noShowFeeCents(),
        request.active(),
        now
    );
    return store.saveAgenda(tenantId, updated, tx);
  }

  public AgendaCreditsUpgradeResponse upgradeAgendaCredits(
      String tenantId,
      String agendaId,
      AgendaCreditsUpgradeRequest request,
      String idempotencyKey
  ) {
    Agenda agenda = resolveAgenda(tenantId, agendaId);
    BillingCustomerResponse customer = ensureBillingCustomer(tenantId);
    BillingChargeCreateRequest chargeRequest = new BillingChargeCreateRequest(
        customer.id(),
        creditsProperties.getPackagePriceCents(),
        creditsProperties.getPackageCurrency(),
        creditsProperties.getPackageDescription(),
        LocalDate.now(resolveZone(agenda.timezone())),
        Map.of("agendaId", agenda.id(), "package", creditsProperties.getPackageName())
    );
    BillingChargeResponse charge = billingSaasClient.createCharge(tenantId, chargeRequest, idempotencyKey);
    BillingPixGenerateResponse pix = billingSaasClient.generatePix(
        tenantId,
        new BillingPixGenerateRequest(charge.id(), creditsProperties.getPixExpiresInSeconds()),
        idempotencyKey == null ? null : idempotencyKey + ":pix"
    );

    Instant now = Instant.now(clock);
    AgendaCreditCharge creditCharge = new AgendaCreditCharge(
        charge.id(),
        tenantId,
        agenda.id(),
        creditsProperties.getPackageCredits(),
        creditsProperties.getPackagePriceCents(),
        charge.status() == null ? "PENDING" : charge.status(),
        pix.providerChargeId(),
        pix.pixCopyPaste(),
        pix.expiresAt(),
        now,
        now,
        null
    );
    store.saveAgendaCreditCharge(tenantId, creditCharge);

    long balance = creditsRemaining(tenantId, agenda.id());
    return new AgendaCreditsUpgradeResponse(
        agenda.id(),
        charge.id(),
        creditCharge.status(),
        creditCharge.creditsAdded(),
        balance,
        creditCharge.providerChargeId(),
        creditCharge.pixCopyPaste(),
        creditCharge.pixExpiresAt()
    );
  }

  public AgendaCreditsStatusResponse creditsStatus(String tenantId, String agendaId, String chargeId) {
    if (chargeId == null || chargeId.isBlank()) {
      long balance = creditsRemaining(tenantId, agendaId);
      return new AgendaCreditsStatusResponse(agendaId, null, "N/A", 0, balance);
    }
    AgendaCreditCharge charge = store.findAgendaCreditCharge(tenantId, chargeId)
        .orElseThrow(() -> new ResourceNotFoundException("Charge not found: " + chargeId));
    if (!charge.agendaId().equals(agendaId)) {
      throw new ResourceNotFoundException("Charge not found for agenda: " + agendaId);
    }
    BillingChargeStatusResponse status = billingSaasClient.getChargeStatus(tenantId, chargeId);
    Instant now = Instant.now(clock);
    String resolvedStatus = status.status() == null ? charge.status() : status.status();
    AgendaCreditCharge updated = charge.withStatus(resolvedStatus, now);
    if ("PAID".equalsIgnoreCase(resolvedStatus) && updated.appliedAt() == null) {
      applyAgendaCredits(tenantId, agendaId, updated.creditsAdded(), now);
      updated = updated.withApplied(now);
    }
    store.saveAgendaCreditCharge(tenantId, updated);
    long balance = creditsRemaining(tenantId, agendaId);
    return new AgendaCreditsStatusResponse(agendaId, chargeId, resolvedStatus, updated.creditsAdded(), balance);
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
    Agenda agenda = resolveAgenda(tenantId, agendaId);

    ZoneId zone = resolveZone(zoneId == null ? agenda.timezone() : zoneId);
    ZonedDateTime dayStart = ZonedDateTime.of(date, LocalTime.parse(agenda.workingHoursStart()), zone);
    ZonedDateTime dayEnd = ZonedDateTime.of(date, LocalTime.parse(agenda.workingHoursEnd()), zone);

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
      cursor = cursor.plusMinutes(agenda.slotStepMin());
    }

    return new SlotsResponse(serviceId, date.toString(), agendaId, agenda.slotStepMin(), slots);
  }

  public ReservationResponse reserve(String tenantId, ReservationRequest request, IdempotencyTransaction tx) {
    ServiceDef service = store.findServiceById(tenantId, request.serviceId())
        .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + request.serviceId()));
    Agenda agenda = resolveAgenda(tenantId, request.agendaId());

    Instant startAt = request.startAt();
    int totalDurationMin = service.durationMin() + service.prepMin() + service.bufferMin();
    Instant endAt = startAt.plus(Duration.ofMinutes(totalDurationMin));

    ZoneId zone = resolveZone(agenda.timezone());
    LocalDate date = LocalDate.ofInstant(startAt, zone);
    ZonedDateTime dayStart = ZonedDateTime.of(date, LocalTime.parse(agenda.workingHoursStart()), zone);
    ZonedDateTime dayEnd = ZonedDateTime.of(date, LocalTime.parse(agenda.workingHoursEnd()), zone);

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
        null,
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
    if (appointment.status() == AppointmentStatus.CANCELLED
        || appointment.status() == AppointmentStatus.NO_SHOW
        || appointment.status() == AppointmentStatus.ATTENDED) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "Appointment cannot be confirmed");
    }
    if (appointment.status() == AppointmentStatus.CONFIRMED) {
      return new ConfirmResponse(appointment.id(), appointment.status().name());
    }
    if (appointment.holdExpiresAt() != null && now.isAfter(appointment.holdExpiresAt())) {
      throw new HoldExpiredException();
    }

    AgendaCredits credits = ensureAgendaCredits(tenantId, appointment.agendaId(), now);
    if (credits.remaining() <= 0) {
      throw new AgendaCreditsExceededException();
    }
    AgendaCredits updatedCredits = credits.withRemaining(credits.remaining() - 1, now);
    store.saveAgendaCredits(tenantId, updatedCredits);

    Appointment updated = appointment.withStatus(AppointmentStatus.CONFIRMED, now);
    store.saveAppointment(tenantId, updated);
    publishEvent("SCHEDULING_APPOINTMENT_CONFIRMED", tenantId, appointmentId, now);
    return new ConfirmResponse(updated.id(), updated.status().name());
  }

  public CancelResponse cancel(String tenantId, String appointmentId) {
    Appointment appointment = store.findAppointment(tenantId, appointmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

    Instant now = Instant.now(clock);
    long fee = calculateCancellationFee(appointment, now);

    Appointment updated = appointment.withStatus(AppointmentStatus.CANCELLED, now);
    store.saveAppointment(tenantId, updated);
    publishEvent("SCHEDULING_CANCELLED", tenantId, appointmentId, now);
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

  public AttendedResponse attended(String tenantId, String appointmentId) {
    Appointment appointment = store.findAppointment(tenantId, appointmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

    Instant now = Instant.now(clock);
    Appointment updated = appointment.withAttended(now);
    store.saveAppointment(tenantId, updated);
    return new AttendedResponse(updated.id(), updated.status().name());
  }

  public List<FineResponse> listFines(String tenantId, String agendaId, FineStatus status) {
    return store.listFines(tenantId, agendaId, status).stream()
        .sorted(Comparator.comparing(Fine::createdAt))
        .map(this::toFineResponse)
        .collect(Collectors.toList());
  }

  public FineResponse waiveFine(String tenantId, String fineId) {
    Fine fine = store.findFine(tenantId, fineId)
        .orElseThrow(() -> new ResourceNotFoundException("Fine not found: " + fineId));
    Instant now = Instant.now(clock);
    Fine updated = fine.withStatus(FineStatus.WAIVED, now);
    store.saveFine(tenantId, updated);
    return toFineResponse(updated);
  }

  public void processNoShows(String tenantId) {
    if (!noShowProperties.isEnabled()) {
      return;
    }
    Instant now = Instant.now(clock);
    Instant before = now.minus(Duration.ofMinutes(noShowProperties.getGraceMin()));
    List<Appointment> candidates = store.listAppointmentsByStatusBefore(
        tenantId,
        AppointmentStatus.CONFIRMED,
        before,
        noShowProperties.getMaxPerRun()
    );
    for (Appointment appointment : candidates) {
      handleNoShow(tenantId, appointment, now);
    }
  }

  public AgendaResponse agenda(String tenantId, String agendaId, Instant from, Instant to) {
    resolveAgenda(tenantId, agendaId);
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

  private FineResponse toFineResponse(Fine fine) {
    return new FineResponse(
        fine.id(),
        fine.agendaId(),
        fine.appointmentId(),
        fine.amountCents(),
        fine.status().name(),
        fine.createdAt(),
        fine.updatedAt()
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

  private Agenda resolveAgenda(String tenantId, String agendaId) {
    String resolvedId = (agendaId == null || agendaId.isBlank()) ? "main" : agendaId;
    return store.findAgenda(tenantId, resolvedId)
        .orElseGet(() -> createDefaultAgenda(tenantId, resolvedId));
  }

  private Agenda createDefaultAgenda(String tenantId, String agendaId) {
    Instant now = Instant.now(clock);
    Agenda agenda = new Agenda(
        agendaId,
        tenantId,
        "Agenda principal",
        properties.getTimezone(),
        properties.getDayStart(),
        properties.getDayEnd(),
        properties.getSlotStepMin(),
        0,
        true,
        now,
        now
    );
    return store.saveAgenda(tenantId, agenda);
  }

  private AgendaCredits ensureAgendaCredits(String tenantId, String agendaId, Instant now) {
    return store.findAgendaCredits(tenantId, agendaId)
        .orElseGet(() -> {
          AgendaCredits credits = new AgendaCredits(tenantId, agendaId, creditsProperties.getDefaultCredits(), now);
          return store.saveAgendaCredits(tenantId, credits);
        });
  }

  private long creditsRemaining(String tenantId, String agendaId) {
    return store.findAgendaCredits(tenantId, agendaId)
        .map(AgendaCredits::remaining)
        .orElse(creditsProperties.getDefaultCredits());
  }

  private void applyAgendaCredits(String tenantId, String agendaId, long creditsToAdd, Instant now) {
    AgendaCredits current = ensureAgendaCredits(tenantId, agendaId, now);
    long updated = current.remaining() + creditsToAdd;
    store.saveAgendaCredits(tenantId, current.withRemaining(updated, now));
  }

  private BillingCustomerResponse ensureBillingCustomer(String tenantId) {
    BillingCustomerCreateRequest request = new BillingCustomerCreateRequest(
        "Agenda Credits",
        "00000000000",
        "financeiro@apipratudo.com",
        "+5500000000000",
        tenantId,
        Map.of("source", "scheduling")
    );
    return billingSaasClient.createCustomer(tenantId, request);
  }

  private void publishEvent(String event, String tenantId, String appointmentId, Instant when) {
    WebhookEventRequest request = new WebhookEventRequest(
        event,
        tenantId,
        new WebhookEventData(appointmentId, event, when),
        when
    );
    webhookEventClient.publishEvent(request, null);
  }

  private void handleNoShow(String tenantId, Appointment appointment, Instant now) {
    if (appointment.status() != AppointmentStatus.CONFIRMED) {
      return;
    }
    Appointment updated = appointment.withNoShow(now);
    store.saveAppointment(tenantId, updated);

    long fineAmount = appointment.noShowFeeCents();
    if (fineAmount <= 0) {
      Agenda agenda = resolveAgenda(tenantId, appointment.agendaId());
      fineAmount = agenda.noShowFeeCents();
    }

    if (fineAmount > 0) {
      Fine fine = new Fine(
          "fine_" + UUID.randomUUID(),
          tenantId,
          appointment.agendaId(),
          appointment.id(),
          fineAmount,
          FineStatus.PENDING,
          now,
          now
      );
      store.saveFine(tenantId, fine);
      publishEvent("SCHEDULING_FINE_CREATED", tenantId, appointment.id(), now);
    }

    publishEvent("SCHEDULING_NO_SHOW", tenantId, appointment.id(), now);
  }
}
