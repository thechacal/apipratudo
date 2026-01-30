package com.apipratudo.scheduling.repository;

import com.apipratudo.scheduling.config.FirestoreProperties;
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
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(Firestore.class)
public class FirestoreSchedulingStore implements SchedulingStore {

  private static final String TENANTS_COLLECTION = "tenants";

  private final Firestore firestore;
  private final FirestoreProperties properties;

  public FirestoreSchedulingStore(Firestore firestore, FirestoreProperties properties) {
    this.firestore = firestore;
    this.properties = properties;
  }

  @Override
  public ServiceDef saveService(String tenantId, ServiceDef service) {
    ensureTenant(tenantId);
    Map<String, Object> data = toServiceDocument(service, tenantId);
    try {
      firestore.collection(serviceCollection(tenantId))
          .document(service.id())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Service save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save service", e);
    }
    return service;
  }

  @Override
  public ServiceDef saveService(String tenantId, ServiceDef service, IdempotencyTransaction transaction) {
    if (transaction.isNoop()) {
      return saveService(tenantId, service);
    }
    transaction.set(serviceCollection(tenantId), service.id(), toServiceDocument(service, tenantId));
    return service;
  }

  @Override
  public List<ServiceDef> listServices(String tenantId) {
    try {
      QuerySnapshot snapshot = firestore.collection(serviceCollection(tenantId)).get().get();
      List<ServiceDef> result = new ArrayList<>();
      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        ServiceDef service = fromServiceSnapshot(doc);
        if (service != null) {
          result.add(service);
        }
      }
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Service list interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to list services", e);
    }
  }

  @Override
  public Optional<ServiceDef> findServiceById(String tenantId, String serviceId) {
    try {
      DocumentSnapshot snapshot = firestore.collection(serviceCollection(tenantId))
          .document(serviceId)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromServiceSnapshot(snapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Service lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch service", e);
    }
  }

  @Override
  public Appointment saveAppointment(String tenantId, Appointment appointment) {
    ensureTenant(tenantId);
    Map<String, Object> data = toAppointmentDocument(appointment, tenantId);
    try {
      firestore.collection(appointmentCollection(tenantId))
          .document(appointment.id())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Appointment save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save appointment", e);
    }
    return appointment;
  }

  @Override
  public Appointment saveAppointment(String tenantId, Appointment appointment, IdempotencyTransaction transaction) {
    if (transaction.isNoop()) {
      return saveAppointment(tenantId, appointment);
    }
    transaction.set(appointmentCollection(tenantId), appointment.id(), toAppointmentDocument(appointment, tenantId));
    return appointment;
  }

  @Override
  public Optional<Appointment> findAppointment(String tenantId, String appointmentId) {
    try {
      DocumentSnapshot snapshot = firestore.collection(appointmentCollection(tenantId))
          .document(appointmentId)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromAppointmentSnapshot(snapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Appointment lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch appointment", e);
    }
  }

  @Override
  public List<Appointment> listAppointments(String tenantId, String agendaId, Instant from, Instant to) {
    try {
      QuerySnapshot snapshot = firestore.collection(appointmentCollection(tenantId))
          .whereEqualTo("agendaId", agendaId)
          .whereGreaterThanOrEqualTo("startAt", Timestamp.ofTimeSecondsAndNanos(from.getEpochSecond(), from.getNano()))
          .whereLessThanOrEqualTo("startAt", Timestamp.ofTimeSecondsAndNanos(to.getEpochSecond(), to.getNano()))
          .get()
          .get();
      List<Appointment> result = new ArrayList<>();
      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        Appointment appointment = fromAppointmentSnapshot(doc);
        if (appointment != null) {
          result.add(appointment);
        }
      }
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Appointment list interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to list appointments", e);
    }
  }

  @Override
  public List<Appointment> listAppointmentsByStatusBefore(
      String tenantId,
      AppointmentStatus status,
      Instant before,
      int limit
  ) {
    try {
      Query query = firestore.collection(appointmentCollection(tenantId))
          .whereEqualTo("status", status.name())
          .whereLessThanOrEqualTo("startAt", Timestamp.ofTimeSecondsAndNanos(before.getEpochSecond(), before.getNano()))
          .orderBy("startAt", Query.Direction.ASCENDING);
      if (limit > 0) {
        query = query.limit(limit);
      }
      QuerySnapshot snapshot = query.get().get();
      List<Appointment> result = new ArrayList<>();
      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        Appointment appointment = fromAppointmentSnapshot(doc);
        if (appointment != null) {
          result.add(appointment);
        }
      }
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Appointment status list interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to list appointments by status", e);
    }
  }

  @Override
  public Agenda saveAgenda(String tenantId, Agenda agenda) {
    ensureTenant(tenantId);
    Map<String, Object> data = toAgendaDocument(agenda, tenantId);
    try {
      firestore.collection(agendaCollection(tenantId))
          .document(agenda.id())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Agenda save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save agenda", e);
    }
    return agenda;
  }

  @Override
  public Agenda saveAgenda(String tenantId, Agenda agenda, IdempotencyTransaction transaction) {
    if (transaction.isNoop()) {
      return saveAgenda(tenantId, agenda);
    }
    transaction.set(agendaCollection(tenantId), agenda.id(), toAgendaDocument(agenda, tenantId));
    return agenda;
  }

  @Override
  public Optional<Agenda> findAgenda(String tenantId, String agendaId) {
    try {
      DocumentSnapshot snapshot = firestore.collection(agendaCollection(tenantId))
          .document(agendaId)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromAgendaSnapshot(snapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Agenda lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch agenda", e);
    }
  }

  @Override
  public List<Agenda> listAgendas(String tenantId) {
    try {
      QuerySnapshot snapshot = firestore.collection(agendaCollection(tenantId)).get().get();
      List<Agenda> result = new ArrayList<>();
      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        Agenda agenda = fromAgendaSnapshot(doc);
        if (agenda != null) {
          result.add(agenda);
        }
      }
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Agenda list interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to list agendas", e);
    }
  }

  @Override
  public AgendaCredits saveAgendaCredits(String tenantId, AgendaCredits credits) {
    ensureTenant(tenantId);
    Map<String, Object> data = toAgendaCreditsDocument(credits);
    try {
      firestore.collection(agendaCreditsCollection(tenantId))
          .document(credits.agendaId())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Agenda credits save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save agenda credits", e);
    }
    return credits;
  }

  @Override
  public Optional<AgendaCredits> findAgendaCredits(String tenantId, String agendaId) {
    try {
      DocumentSnapshot snapshot = firestore.collection(agendaCreditsCollection(tenantId))
          .document(agendaId)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromAgendaCreditsSnapshot(snapshot, agendaId));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Agenda credits lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch agenda credits", e);
    }
  }

  @Override
  public AgendaCreditCharge saveAgendaCreditCharge(String tenantId, AgendaCreditCharge charge) {
    ensureTenant(tenantId);
    Map<String, Object> data = toAgendaChargeDocument(charge);
    try {
      firestore.collection(agendaChargesCollection(tenantId))
          .document(charge.chargeId())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Agenda charge save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save agenda charge", e);
    }
    return charge;
  }

  @Override
  public Optional<AgendaCreditCharge> findAgendaCreditCharge(String tenantId, String chargeId) {
    try {
      DocumentSnapshot snapshot = firestore.collection(agendaChargesCollection(tenantId))
          .document(chargeId)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromAgendaChargeSnapshot(snapshot, tenantId));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Agenda charge lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch agenda charge", e);
    }
  }

  @Override
  public List<Fine> listFines(String tenantId, String agendaId, FineStatus status) {
    try {
      Query query = firestore.collection(finesCollection(tenantId));
      if (agendaId != null) {
        query = query.whereEqualTo("agendaId", agendaId);
      }
      if (status != null) {
        query = query.whereEqualTo("status", status.name());
      }
      QuerySnapshot snapshot = query.get().get();
      List<Fine> result = new ArrayList<>();
      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        Fine fine = fromFineSnapshot(doc, tenantId);
        if (fine != null) {
          result.add(fine);
        }
      }
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Fine list interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to list fines", e);
    }
  }

  @Override
  public Fine saveFine(String tenantId, Fine fine) {
    ensureTenant(tenantId);
    Map<String, Object> data = toFineDocument(fine);
    try {
      firestore.collection(finesCollection(tenantId))
          .document(fine.id())
          .set(data)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Fine save interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to save fine", e);
    }
    return fine;
  }

  @Override
  public Optional<Fine> findFine(String tenantId, String fineId) {
    try {
      DocumentSnapshot snapshot = firestore.collection(finesCollection(tenantId))
          .document(fineId)
          .get()
          .get();
      if (!snapshot.exists()) {
        return Optional.empty();
      }
      return Optional.ofNullable(fromFineSnapshot(snapshot, tenantId));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Fine lookup interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to fetch fine", e);
    }
  }

  @Override
  public void ensureTenant(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return;
    }
    try {
      DocumentReference doc = firestore.collection(TENANTS_COLLECTION).document(tenantId);
      Map<String, Object> data = new HashMap<>();
      data.put("updatedAt", Timestamp.now());
      doc.set(data).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Tenant ensure interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to ensure tenant", e);
    }
  }

  @Override
  public Set<String> listTenants() {
    try {
      Iterable<DocumentReference> docs = firestore.collection(TENANTS_COLLECTION).listDocuments();
      Set<String> tenantIds = new HashSet<>();
      for (DocumentReference ref : docs) {
        tenantIds.add(ref.getId());
      }
      return tenantIds;
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to list tenants", ex);
    }
  }

  private Map<String, Object> toServiceDocument(ServiceDef service, String tenantId) {
    Map<String, Object> data = new HashMap<>();
    data.put("tenantId", tenantId);
    data.put("name", service.name());
    data.put("durationMin", service.durationMin());
    data.put("prepMin", service.prepMin());
    data.put("bufferMin", service.bufferMin());
    data.put("noShowFeeCents", service.noShowFeeCents());
    data.put("active", service.active());
    data.put("createdAt", toTimestamp(service.createdAt()));
    return data;
  }

  private Map<String, Object> toAgendaDocument(Agenda agenda, String tenantId) {
    Map<String, Object> data = new HashMap<>();
    data.put("tenantId", tenantId);
    data.put("name", agenda.name());
    data.put("timezone", agenda.timezone());
    data.put("workingHoursStart", agenda.workingHoursStart());
    data.put("workingHoursEnd", agenda.workingHoursEnd());
    data.put("slotStepMin", agenda.slotStepMin());
    data.put("noShowFeeCents", agenda.noShowFeeCents());
    data.put("active", agenda.active());
    data.put("createdAt", toTimestamp(agenda.createdAt()));
    data.put("updatedAt", toTimestamp(agenda.updatedAt()));
    return data;
  }

  private ServiceDef fromServiceSnapshot(DocumentSnapshot snapshot) {
    String id = snapshot.getId();
    String tenantId = snapshot.getString("tenantId");
    String name = snapshot.getString("name");
    Long durationMin = snapshot.getLong("durationMin");
    Long prepMin = snapshot.getLong("prepMin");
    Long bufferMin = snapshot.getLong("bufferMin");
    Long noShowFeeCents = snapshot.getLong("noShowFeeCents");
    Boolean active = snapshot.getBoolean("active");
    Instant createdAt = fromTimestamp(snapshot.getTimestamp("createdAt"));
    if (name == null || durationMin == null || prepMin == null || bufferMin == null || active == null) {
      return null;
    }
    return new ServiceDef(
        id,
        tenantId,
        name,
        durationMin.intValue(),
        prepMin.intValue(),
        bufferMin.intValue(),
        noShowFeeCents == null ? 0 : noShowFeeCents,
        active,
        createdAt
    );
  }

  private Agenda fromAgendaSnapshot(DocumentSnapshot snapshot) {
    String id = snapshot.getId();
    String tenantId = snapshot.getString("tenantId");
    String name = snapshot.getString("name");
    String timezone = snapshot.getString("timezone");
    String workingHoursStart = snapshot.getString("workingHoursStart");
    String workingHoursEnd = snapshot.getString("workingHoursEnd");
    Long slotStepMin = snapshot.getLong("slotStepMin");
    Long noShowFeeCents = snapshot.getLong("noShowFeeCents");
    Boolean active = snapshot.getBoolean("active");
    Instant createdAt = fromTimestamp(snapshot.getTimestamp("createdAt"));
    Instant updatedAt = fromTimestamp(snapshot.getTimestamp("updatedAt"));
    if (name == null || timezone == null || workingHoursStart == null || workingHoursEnd == null
        || slotStepMin == null || active == null) {
      return null;
    }
    return new Agenda(
        id,
        tenantId,
        name,
        timezone,
        workingHoursStart,
        workingHoursEnd,
        slotStepMin.intValue(),
        noShowFeeCents == null ? 0 : noShowFeeCents,
        active,
        createdAt,
        updatedAt
    );
  }

  private Map<String, Object> toAgendaCreditsDocument(AgendaCredits credits) {
    Map<String, Object> data = new HashMap<>();
    data.put("remaining", credits.remaining());
    data.put("updatedAt", toTimestamp(credits.updatedAt()));
    return data;
  }

  private AgendaCredits fromAgendaCreditsSnapshot(DocumentSnapshot snapshot, String agendaId) {
    Long remaining = snapshot.getLong("remaining");
    Instant updatedAt = fromTimestamp(snapshot.getTimestamp("updatedAt"));
    if (remaining == null) {
      return null;
    }
    return new AgendaCredits(snapshot.getReference().getParent().getParent().getId(), agendaId, remaining, updatedAt);
  }

  private Map<String, Object> toAgendaChargeDocument(AgendaCreditCharge charge) {
    Map<String, Object> data = new HashMap<>();
    data.put("agendaId", charge.agendaId());
    data.put("creditsAdded", charge.creditsAdded());
    data.put("amountCents", charge.amountCents());
    data.put("status", charge.status());
    data.put("providerChargeId", charge.providerChargeId());
    data.put("pixCopyPaste", charge.pixCopyPaste());
    data.put("pixExpiresAt", toTimestamp(charge.pixExpiresAt()));
    data.put("createdAt", toTimestamp(charge.createdAt()));
    data.put("updatedAt", toTimestamp(charge.updatedAt()));
    data.put("appliedAt", toTimestamp(charge.appliedAt()));
    return data;
  }

  private AgendaCreditCharge fromAgendaChargeSnapshot(DocumentSnapshot snapshot, String tenantId) {
    String agendaId = snapshot.getString("agendaId");
    Long creditsAdded = snapshot.getLong("creditsAdded");
    Long amountCents = snapshot.getLong("amountCents");
    String status = snapshot.getString("status");
    String providerChargeId = snapshot.getString("providerChargeId");
    String pixCopyPaste = snapshot.getString("pixCopyPaste");
    Instant pixExpiresAt = fromTimestamp(snapshot.getTimestamp("pixExpiresAt"));
    Instant createdAt = fromTimestamp(snapshot.getTimestamp("createdAt"));
    Instant updatedAt = fromTimestamp(snapshot.getTimestamp("updatedAt"));
    Instant appliedAt = fromTimestamp(snapshot.getTimestamp("appliedAt"));
    if (agendaId == null || creditsAdded == null || amountCents == null) {
      return null;
    }
    return new AgendaCreditCharge(
        snapshot.getId(),
        tenantId,
        agendaId,
        creditsAdded,
        amountCents,
        status == null ? "PENDING" : status,
        providerChargeId,
        pixCopyPaste,
        pixExpiresAt,
        createdAt,
        updatedAt,
        appliedAt
    );
  }

  private Map<String, Object> toFineDocument(Fine fine) {
    Map<String, Object> data = new HashMap<>();
    data.put("agendaId", fine.agendaId());
    data.put("appointmentId", fine.appointmentId());
    data.put("amountCents", fine.amountCents());
    data.put("status", fine.status().name());
    data.put("createdAt", toTimestamp(fine.createdAt()));
    data.put("updatedAt", toTimestamp(fine.updatedAt()));
    return data;
  }

  private Fine fromFineSnapshot(DocumentSnapshot snapshot, String tenantId) {
    String agendaId = snapshot.getString("agendaId");
    String appointmentId = snapshot.getString("appointmentId");
    Long amountCents = snapshot.getLong("amountCents");
    String status = snapshot.getString("status");
    Instant createdAt = fromTimestamp(snapshot.getTimestamp("createdAt"));
    Instant updatedAt = fromTimestamp(snapshot.getTimestamp("updatedAt"));
    if (agendaId == null || appointmentId == null || amountCents == null) {
      return null;
    }
    FineStatus fineStatus = status == null ? FineStatus.PENDING : FineStatus.valueOf(status);
    return new Fine(
        snapshot.getId(),
        tenantId,
        agendaId,
        appointmentId,
        amountCents,
        fineStatus,
        createdAt,
        updatedAt
    );
  }

  private Map<String, Object> toAppointmentDocument(Appointment appointment, String tenantId) {
    Map<String, Object> data = new HashMap<>();
    data.put("tenantId", tenantId);
    data.put("serviceId", appointment.serviceId());
    data.put("agendaId", appointment.agendaId());
    data.put("startAt", toTimestamp(appointment.startAt()));
    data.put("endAt", toTimestamp(appointment.endAt()));
    data.put("status", appointment.status().name());
    data.put("holdExpiresAt", toTimestamp(appointment.holdExpiresAt()));
    data.put("notes", appointment.notes());
    data.put("noShowFeeCents", appointment.noShowFeeCents());
    data.put("createdAt", toTimestamp(appointment.createdAt()));
    data.put("updatedAt", toTimestamp(appointment.updatedAt()));
    data.put("lastNotifiedAt", toTimestamp(appointment.lastNotifiedAt()));
    data.put("lastNotificationType", appointment.lastNotificationType());
    data.put("attendedAt", toTimestamp(appointment.attendedAt()));
    data.put("noShowAt", toTimestamp(appointment.noShowAt()));
    if (appointment.customer() != null) {
      Map<String, Object> customer = new HashMap<>();
      customer.put("name", appointment.customer().name());
      customer.put("phone", appointment.customer().phone());
      customer.put("email", appointment.customer().email());
      data.put("customer", customer);
    }
    return data;
  }

  private Appointment fromAppointmentSnapshot(DocumentSnapshot snapshot) {
    String id = snapshot.getId();
    String tenantId = snapshot.getString("tenantId");
    String serviceId = snapshot.getString("serviceId");
    String agendaId = snapshot.getString("agendaId");
    String status = snapshot.getString("status");
    Instant startAt = fromTimestamp(snapshot.getTimestamp("startAt"));
    Instant endAt = fromTimestamp(snapshot.getTimestamp("endAt"));
    Instant holdExpiresAt = fromTimestamp(snapshot.getTimestamp("holdExpiresAt"));
    Long noShowFeeCents = snapshot.getLong("noShowFeeCents");
    Instant createdAt = fromTimestamp(snapshot.getTimestamp("createdAt"));
    Instant updatedAt = fromTimestamp(snapshot.getTimestamp("updatedAt"));
    Instant lastNotifiedAt = fromTimestamp(snapshot.getTimestamp("lastNotifiedAt"));
    String lastNotificationType = snapshot.getString("lastNotificationType");
    Instant attendedAt = fromTimestamp(snapshot.getTimestamp("attendedAt"));
    Instant noShowAt = fromTimestamp(snapshot.getTimestamp("noShowAt"));
    String notes = snapshot.getString("notes");
    AppointmentStatus appointmentStatus = status == null ? AppointmentStatus.HELD : AppointmentStatus.valueOf(status);

    Customer customer = null;
    Object rawCustomer = snapshot.get("customer");
    if (rawCustomer instanceof Map<?, ?> map) {
      Object name = map.get("name");
      Object phone = map.get("phone");
      Object email = map.get("email");
      customer = new Customer(
          name == null ? null : name.toString(),
          phone == null ? null : phone.toString(),
          email == null ? null : email.toString()
      );
    }

    if (serviceId == null || agendaId == null || startAt == null || endAt == null) {
      return null;
    }

    return new Appointment(
        id,
        tenantId,
        serviceId,
        agendaId,
        startAt,
        endAt,
        appointmentStatus,
        holdExpiresAt,
        customer,
        notes,
        noShowFeeCents == null ? 0 : noShowFeeCents,
        createdAt,
        updatedAt,
        lastNotifiedAt,
        lastNotificationType,
        attendedAt,
        noShowAt
    );
  }

  private String serviceCollection(String tenantId) {
    return TENANTS_COLLECTION + "/" + tenantId + "/" + properties.getCollections().getServices();
  }

  private String appointmentCollection(String tenantId) {
    return TENANTS_COLLECTION + "/" + tenantId + "/" + properties.getCollections().getAppointments();
  }

  private String agendaCollection(String tenantId) {
    return TENANTS_COLLECTION + "/" + tenantId + "/" + properties.getCollections().getAgendas();
  }

  private String agendaCreditsCollection(String tenantId) {
    return TENANTS_COLLECTION + "/" + tenantId + "/" + properties.getCollections().getAgendaCredits();
  }

  private String agendaChargesCollection(String tenantId) {
    return TENANTS_COLLECTION + "/" + tenantId + "/" + properties.getCollections().getAgendaCharges();
  }

  private String finesCollection(String tenantId) {
    return TENANTS_COLLECTION + "/" + tenantId + "/" + properties.getCollections().getFines();
  }

  private Timestamp toTimestamp(Instant instant) {
    if (instant == null) {
      return null;
    }
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
  }

  private Instant fromTimestamp(Timestamp timestamp) {
    if (timestamp == null) {
      return null;
    }
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }
}
