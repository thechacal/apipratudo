package com.apipratudo.scheduling.repository;

import com.apipratudo.scheduling.config.FirestoreProperties;
import com.apipratudo.scheduling.idempotency.IdempotencyTransaction;
import com.apipratudo.scheduling.model.Appointment;
import com.apipratudo.scheduling.model.AppointmentStatus;
import com.apipratudo.scheduling.model.Customer;
import com.apipratudo.scheduling.model.ServiceDef;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        lastNotificationType
    );
  }

  private String serviceCollection(String tenantId) {
    return TENANTS_COLLECTION + "/" + tenantId + "/" + properties.getCollections().getServices();
  }

  private String appointmentCollection(String tenantId) {
    return TENANTS_COLLECTION + "/" + tenantId + "/" + properties.getCollections().getAppointments();
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
