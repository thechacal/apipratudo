package com.apipratudo.helpdesk.service;

import com.apipratudo.helpdesk.config.HelpdeskProperties;
import com.apipratudo.helpdesk.dto.AssignRequest;
import com.apipratudo.helpdesk.dto.MessageCreateRequest;
import com.apipratudo.helpdesk.dto.StatusUpdateRequest;
import com.apipratudo.helpdesk.dto.TemplateCreateRequest;
import com.apipratudo.helpdesk.dto.TicketCreateRequest;
import com.apipratudo.helpdesk.error.ApiException;
import com.apipratudo.helpdesk.model.Message;
import com.apipratudo.helpdesk.model.MessageDirection;
import com.apipratudo.helpdesk.model.Template;
import com.apipratudo.helpdesk.model.Ticket;
import com.apipratudo.helpdesk.model.TicketStatus;
import com.apipratudo.helpdesk.repository.HelpdeskStore;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HelpdeskService {

  private final HelpdeskStore store;
  private final HelpdeskProperties properties;
  private final Clock clock;

  public HelpdeskService(HelpdeskStore store, HelpdeskProperties properties, Clock clock) {
    this.store = store;
    this.properties = properties;
    this.clock = clock;
  }

  public Ticket createTicket(String tenantId, TicketCreateRequest request) {
    Instant now = Instant.now(clock);
    Ticket ticket = new Ticket(
        "tkt_" + UUID.randomUUID(),
        tenantId,
        TicketStatus.OPEN,
        null,
        request.customerWaId(),
        now,
        now,
        now.plus(properties.getSlaMinutes(), ChronoUnit.MINUTES),
        null,
        "",
        List.of()
    );
    return store.saveTicket(ticket);
  }

  public List<Ticket> listTickets(String tenantId, String status) {
    if (StringUtils.hasText(status)) {
      return store.listTicketsByStatus(tenantId, TicketStatus.valueOf(status));
    }
    return store.listTickets(tenantId);
  }

  public Ticket getTicket(String tenantId, String ticketId) {
    Ticket ticket = store.getTicket(tenantId, ticketId);
    if (ticket == null) {
      throw new ApiException(404, "NOT_FOUND", "Ticket not found");
    }
    return ticket;
  }

  public Message addMessage(String tenantId, String ticketId, MessageCreateRequest request) {
    Ticket ticket = getTicket(tenantId, ticketId);
    MessageDirection direction = MessageDirection.valueOf(request.direction());
    Instant now = Instant.now(clock);
    Message message = new Message(
        "msg_" + UUID.randomUUID(),
        tenantId,
        ticketId,
        direction,
        request.text(),
        now,
        request.providerMessageId()
    );
    store.saveMessage(message);

    Ticket updated = updateTicketWithMessage(ticket, message);
    store.saveTicket(updated);
    return message;
  }

  public List<Message> listMessages(String tenantId, String ticketId) {
    getTicket(tenantId, ticketId);
    return store.listMessages(tenantId, ticketId);
  }

  public Ticket assign(String tenantId, String ticketId, AssignRequest request) {
    Ticket ticket = getTicket(tenantId, ticketId);
    Ticket updated = new Ticket(
        ticket.id(),
        ticket.tenantId(),
        ticket.status(),
        request.assigneeUserId(),
        ticket.customerWaId(),
        ticket.createdAt(),
        Instant.now(clock),
        ticket.slaFirstResponseDueAt(),
        ticket.firstResponseAt(),
        ticket.autoSummary(),
        ticket.intentTags()
    );
    return store.saveTicket(updated);
  }

  public Ticket updateStatus(String tenantId, String ticketId, StatusUpdateRequest request) {
    Ticket ticket = getTicket(tenantId, ticketId);
    TicketStatus status = TicketStatus.valueOf(request.status());
    Ticket updated = new Ticket(
        ticket.id(),
        ticket.tenantId(),
        status,
        ticket.assigneeUserId(),
        ticket.customerWaId(),
        ticket.createdAt(),
        Instant.now(clock),
        ticket.slaFirstResponseDueAt(),
        ticket.firstResponseAt(),
        ticket.autoSummary(),
        ticket.intentTags()
    );
    return store.saveTicket(updated);
  }

  public Template createTemplate(String tenantId, TemplateCreateRequest request) {
    Template template = new Template(
        "tpl_" + UUID.randomUUID(),
        tenantId,
        request.name(),
        request.body(),
        Instant.now(clock)
    );
    return store.saveTemplate(template);
  }

  public Template getTemplate(String tenantId, String templateId) {
    Template template = store.getTemplate(tenantId, templateId);
    if (template == null) {
      throw new ApiException(404, "NOT_FOUND", "Template not found");
    }
    return template;
  }

  public List<Template> listTemplates(String tenantId) {
    return store.listTemplates(tenantId);
  }

  public void ensureBinding(String phoneNumberId, String tenantId) {
    store.saveBinding(phoneNumberId, tenantId);
  }

  public String resolveTenantByPhoneNumberId(String phoneNumberId) {
    return store.findTenantIdByPhoneNumberId(phoneNumberId);
  }

  private Ticket updateTicketWithMessage(Ticket ticket, Message message) {
    Instant now = Instant.now(clock);
    Instant firstResponseAt = ticket.firstResponseAt();
    if (message.direction() == MessageDirection.OUTBOUND && firstResponseAt == null) {
      firstResponseAt = now;
    }

    List<Message> recent = store.listMessages(ticket.tenantId(), ticket.id());
    List<Message> last = recent.size() <= 3
        ? recent
        : recent.subList(recent.size() - 3, recent.size());

    String summary = buildSummary(last);
    List<String> tags = ticket.intentTags();
    if (message.direction() == MessageDirection.INBOUND) {
      tags = updateIntentTags(ticket.intentTags(), message.text());
    }

    return new Ticket(
        ticket.id(),
        ticket.tenantId(),
        ticket.status(),
        ticket.assigneeUserId(),
        ticket.customerWaId(),
        ticket.createdAt(),
        now,
        ticket.slaFirstResponseDueAt(),
        firstResponseAt,
        summary,
        tags
    );
  }

  private String buildSummary(List<Message> messages) {
    StringBuilder builder = new StringBuilder();
    for (Message msg : messages) {
      if (builder.length() > 0) {
        builder.append(" | ");
      }
      builder.append(msg.direction() == MessageDirection.INBOUND ? "C: " : "A: ");
      builder.append(msg.text());
    }
    String summary = builder.toString();
    if (summary.length() > properties.getSummaryMaxChars()) {
      return summary.substring(0, properties.getSummaryMaxChars());
    }
    return summary;
  }

  private List<String> updateIntentTags(List<String> current, String text) {
    Map<String, String> keywords = properties.getIntentKeywords();
    Set<String> tags = new LinkedHashSet<>(current == null ? List.of() : current);
    String normalized = text.toLowerCase(Locale.ROOT);
    for (Map.Entry<String, String> entry : keywords.entrySet()) {
      if (normalized.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
        tags.add(entry.getValue());
      }
    }
    return new ArrayList<>(tags);
  }
}
