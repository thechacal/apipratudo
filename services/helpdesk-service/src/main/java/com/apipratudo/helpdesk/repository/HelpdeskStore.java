package com.apipratudo.helpdesk.repository;

import com.apipratudo.helpdesk.model.IdempotencyRecord;
import com.apipratudo.helpdesk.model.Message;
import com.apipratudo.helpdesk.model.Template;
import com.apipratudo.helpdesk.model.Ticket;
import com.apipratudo.helpdesk.model.TicketStatus;
import java.util.List;

public interface HelpdeskStore {

  Ticket saveTicket(Ticket ticket);

  Ticket getTicket(String tenantId, String ticketId);

  List<Ticket> listTickets(String tenantId);

  List<Ticket> listTicketsByStatus(String tenantId, TicketStatus status);

  Message saveMessage(Message message);

  List<Message> listMessages(String tenantId, String ticketId);

  Template saveTemplate(Template template);

  Template getTemplate(String tenantId, String templateId);

  List<Template> listTemplates(String tenantId);

  void saveBinding(String phoneNumberId, String tenantId);

  String findTenantIdByPhoneNumberId(String phoneNumberId);

  IdempotencyRecord getIdempotency(String key);

  void saveIdempotency(IdempotencyRecord record);
}
