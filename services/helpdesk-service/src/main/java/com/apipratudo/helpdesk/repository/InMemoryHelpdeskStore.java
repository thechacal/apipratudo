package com.apipratudo.helpdesk.repository;

import com.apipratudo.helpdesk.model.IdempotencyRecord;
import com.apipratudo.helpdesk.model.Message;
import com.apipratudo.helpdesk.model.Template;
import com.apipratudo.helpdesk.model.Ticket;
import com.apipratudo.helpdesk.model.TicketStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryHelpdeskStore implements HelpdeskStore {

  private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
  private final Map<String, List<Message>> messages = new ConcurrentHashMap<>();
  private final Map<String, Template> templates = new ConcurrentHashMap<>();
  private final Map<String, String> bindings = new ConcurrentHashMap<>();
  private final Map<String, IdempotencyRecord> idempotency = new ConcurrentHashMap<>();

  @Override
  public Ticket saveTicket(Ticket ticket) {
    tickets.put(ticket.tenantId() + ":" + ticket.id(), ticket);
    return ticket;
  }

  @Override
  public Ticket getTicket(String tenantId, String ticketId) {
    return tickets.get(tenantId + ":" + ticketId);
  }

  @Override
  public List<Ticket> listTickets(String tenantId) {
    return tickets.values().stream()
        .filter(ticket -> tenantId.equals(ticket.tenantId()))
        .collect(Collectors.toList());
  }

  @Override
  public List<Ticket> listTicketsByStatus(String tenantId, TicketStatus status) {
    return tickets.values().stream()
        .filter(ticket -> tenantId.equals(ticket.tenantId()))
        .filter(ticket -> ticket.status() == status)
        .collect(Collectors.toList());
  }

  @Override
  public Message saveMessage(Message message) {
    String key = message.tenantId() + ":" + message.ticketId();
    messages.computeIfAbsent(key, k -> new ArrayList<>()).add(message);
    return message;
  }

  @Override
  public List<Message> listMessages(String tenantId, String ticketId) {
    String key = tenantId + ":" + ticketId;
    return new ArrayList<>(messages.getOrDefault(key, Collections.emptyList()));
  }

  @Override
  public Template saveTemplate(Template template) {
    templates.put(template.tenantId() + ":" + template.id(), template);
    return template;
  }

  @Override
  public Template getTemplate(String tenantId, String templateId) {
    return templates.get(tenantId + ":" + templateId);
  }

  @Override
  public List<Template> listTemplates(String tenantId) {
    return templates.values().stream()
        .filter(template -> tenantId.equals(template.tenantId()))
        .collect(Collectors.toList());
  }

  @Override
  public void saveBinding(String phoneNumberId, String tenantId) {
    bindings.put(phoneNumberId, tenantId);
  }

  @Override
  public String findTenantIdByPhoneNumberId(String phoneNumberId) {
    return bindings.get(phoneNumberId);
  }

  @Override
  public IdempotencyRecord getIdempotency(String key) {
    return idempotency.get(key);
  }

  @Override
  public void saveIdempotency(IdempotencyRecord record) {
    idempotency.put(record.key(), record);
  }
}
