package com.apipratudo.helpdesk.repository;

import com.apipratudo.helpdesk.config.HelpdeskStorageProperties;
import com.apipratudo.helpdesk.config.IdempotencyProperties;
import com.apipratudo.helpdesk.config.WhatsappProperties;
import com.apipratudo.helpdesk.model.IdempotencyRecord;
import com.apipratudo.helpdesk.model.Message;
import com.apipratudo.helpdesk.model.MessageDirection;
import com.apipratudo.helpdesk.model.Template;
import com.apipratudo.helpdesk.model.Ticket;
import com.apipratudo.helpdesk.model.TicketStatus;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class FirestoreHelpdeskStore implements HelpdeskStore {

  private final Firestore firestore;
  private final String ticketsCollection;
  private final String messagesCollection;
  private final String templatesCollection;
  private final String bindingsCollection;
  private final String idempotencyCollection;

  public FirestoreHelpdeskStore(
      Firestore firestore,
      HelpdeskStorageProperties storageProperties,
      WhatsappProperties whatsappProperties,
      IdempotencyProperties idempotencyProperties
  ) {
    this.firestore = firestore;
    this.ticketsCollection = storageProperties.getTicketsCollection();
    this.messagesCollection = storageProperties.getMessagesCollection();
    this.templatesCollection = storageProperties.getTemplatesCollection();
    this.bindingsCollection = whatsappProperties.getBindingsCollection();
    this.idempotencyCollection = idempotencyProperties.getCollection();
  }

  @Override
  public Ticket saveTicket(Ticket ticket) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", ticket.id());
    data.put("tenantId", ticket.tenantId());
    data.put("status", ticket.status().name());
    data.put("assigneeUserId", ticket.assigneeUserId());
    data.put("customerWaId", ticket.customerWaId());
    data.put("createdAt", ticket.createdAt().toString());
    data.put("updatedAt", ticket.updatedAt().toString());
    data.put("slaFirstResponseDueAt", ticket.slaFirstResponseDueAt().toString());
    data.put("firstResponseAt", ticket.firstResponseAt() == null ? null : ticket.firstResponseAt().toString());
    data.put("autoSummary", ticket.autoSummary());
    data.put("intentTags", ticket.intentTags());
    try {
      firestore.collection(ticketsCollection).document(ticket.id()).set(data).get();
      return ticket;
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to save ticket", e);
    }
  }

  @Override
  public Ticket getTicket(String tenantId, String ticketId) {
    try {
      DocumentSnapshot snapshot = firestore.collection(ticketsCollection).document(ticketId).get().get();
      if (!snapshot.exists()) {
        return null;
      }
      if (!tenantId.equals(snapshot.getString("tenantId"))) {
        return null;
      }
      return mapTicket(snapshot);
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to get ticket", e);
    }
  }

  @Override
  public List<Ticket> listTickets(String tenantId) {
    try {
      QuerySnapshot snapshot = firestore.collection(ticketsCollection)
          .whereEqualTo("tenantId", tenantId)
          .get()
          .get();
      List<Ticket> result = new ArrayList<>();
      for (QueryDocumentSnapshot doc : snapshot) {
        result.add(mapTicket(doc));
      }
      return result;
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to list tickets", e);
    }
  }

  @Override
  public List<Ticket> listTicketsByStatus(String tenantId, TicketStatus status) {
    try {
      QuerySnapshot snapshot = firestore.collection(ticketsCollection)
          .whereEqualTo("tenantId", tenantId)
          .whereEqualTo("status", status.name())
          .get()
          .get();
      List<Ticket> result = new ArrayList<>();
      for (QueryDocumentSnapshot doc : snapshot) {
        result.add(mapTicket(doc));
      }
      return result;
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to list tickets by status", e);
    }
  }

  @Override
  public Message saveMessage(Message message) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", message.id());
    data.put("tenantId", message.tenantId());
    data.put("ticketId", message.ticketId());
    data.put("direction", message.direction().name());
    data.put("text", message.text());
    data.put("createdAt", message.createdAt().toString());
    data.put("providerMessageId", message.providerMessageId());
    try {
      firestore.collection(messagesCollection).document(message.id()).set(data).get();
      return message;
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to save message", e);
    }
  }

  @Override
  public List<Message> listMessages(String tenantId, String ticketId) {
    try {
      QuerySnapshot snapshot = firestore.collection(messagesCollection)
          .whereEqualTo("tenantId", tenantId)
          .whereEqualTo("ticketId", ticketId)
          .get()
          .get();
      List<Message> result = new ArrayList<>();
      for (QueryDocumentSnapshot doc : snapshot) {
        result.add(mapMessage(doc));
      }
      return result;
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to list messages", e);
    }
  }

  @Override
  public Template saveTemplate(Template template) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", template.id());
    data.put("tenantId", template.tenantId());
    data.put("name", template.name());
    data.put("body", template.body());
    data.put("createdAt", template.createdAt().toString());
    try {
      firestore.collection(templatesCollection).document(template.id()).set(data).get();
      return template;
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to save template", e);
    }
  }

  @Override
  public Template getTemplate(String tenantId, String templateId) {
    try {
      DocumentSnapshot snapshot = firestore.collection(templatesCollection).document(templateId).get().get();
      if (!snapshot.exists()) {
        return null;
      }
      if (!tenantId.equals(snapshot.getString("tenantId"))) {
        return null;
      }
      return mapTemplate(snapshot);
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to get template", e);
    }
  }

  @Override
  public List<Template> listTemplates(String tenantId) {
    try {
      QuerySnapshot snapshot = firestore.collection(templatesCollection)
          .whereEqualTo("tenantId", tenantId)
          .get()
          .get();
      List<Template> result = new ArrayList<>();
      for (QueryDocumentSnapshot doc : snapshot) {
        result.add(mapTemplate(doc));
      }
      return result;
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to list templates", e);
    }
  }

  @Override
  public void saveBinding(String phoneNumberId, String tenantId) {
    Map<String, Object> data = new HashMap<>();
    data.put("tenantId", tenantId);
    try {
      firestore.collection(bindingsCollection).document(phoneNumberId).set(data).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to save binding", e);
    }
  }

  @Override
  public String findTenantIdByPhoneNumberId(String phoneNumberId) {
    try {
      DocumentSnapshot snapshot = firestore.collection(bindingsCollection).document(phoneNumberId).get().get();
      if (!snapshot.exists()) {
        return null;
      }
      return snapshot.getString("tenantId");
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to lookup binding", e);
    }
  }

  @Override
  public IdempotencyRecord getIdempotency(String key) {
    try {
      DocumentSnapshot snapshot = firestore.collection(idempotencyCollection).document(key).get().get();
      if (!snapshot.exists()) {
        return null;
      }
      return mapIdempotency(snapshot);
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to get idempotency", e);
    }
  }

  @Override
  public void saveIdempotency(IdempotencyRecord record) {
    Map<String, Object> data = new HashMap<>();
    data.put("key", record.key());
    data.put("statusCode", record.statusCode());
    data.put("bodyJson", record.bodyJson());
    data.put("createdAt", record.createdAt().toString());
    data.put("expiresAt", record.expiresAt().toString());
    try {
      firestore.collection(idempotencyCollection).document(record.key()).set(data).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to save idempotency", e);
    }
  }

  private Ticket mapTicket(DocumentSnapshot snapshot) {
    return new Ticket(
        snapshot.getString("id"),
        snapshot.getString("tenantId"),
        TicketStatus.valueOf(snapshot.getString("status")),
        snapshot.getString("assigneeUserId"),
        snapshot.getString("customerWaId"),
        Instant.parse(snapshot.getString("createdAt")),
        Instant.parse(snapshot.getString("updatedAt")),
        Instant.parse(snapshot.getString("slaFirstResponseDueAt")),
        snapshot.getString("firstResponseAt") == null ? null : Instant.parse(snapshot.getString("firstResponseAt")),
        snapshot.getString("autoSummary"),
        snapshot.contains("intentTags") ? (List<String>) snapshot.get("intentTags") : List.of()
    );
  }

  private Message mapMessage(DocumentSnapshot snapshot) {
    return new Message(
        snapshot.getString("id"),
        snapshot.getString("tenantId"),
        snapshot.getString("ticketId"),
        MessageDirection.valueOf(snapshot.getString("direction")),
        snapshot.getString("text"),
        Instant.parse(snapshot.getString("createdAt")),
        snapshot.getString("providerMessageId")
    );
  }

  private Template mapTemplate(DocumentSnapshot snapshot) {
    return new Template(
        snapshot.getString("id"),
        snapshot.getString("tenantId"),
        snapshot.getString("name"),
        snapshot.getString("body"),
        Instant.parse(snapshot.getString("createdAt"))
    );
  }

  private IdempotencyRecord mapIdempotency(DocumentSnapshot snapshot) {
    return new IdempotencyRecord(
        snapshot.getString("key"),
        snapshot.getLong("statusCode").intValue(),
        snapshot.getString("bodyJson"),
        Instant.parse(snapshot.getString("createdAt")),
        Instant.parse(snapshot.getString("expiresAt"))
    );
  }
}
