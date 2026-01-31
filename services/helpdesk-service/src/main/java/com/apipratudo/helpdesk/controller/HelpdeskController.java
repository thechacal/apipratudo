package com.apipratudo.helpdesk.controller;

import com.apipratudo.helpdesk.dto.AssignRequest;
import com.apipratudo.helpdesk.dto.MessageCreateRequest;
import com.apipratudo.helpdesk.dto.MessageResponse;
import com.apipratudo.helpdesk.dto.StatusUpdateRequest;
import com.apipratudo.helpdesk.dto.TemplateCreateRequest;
import com.apipratudo.helpdesk.dto.TemplateResponse;
import com.apipratudo.helpdesk.dto.TicketCreateRequest;
import com.apipratudo.helpdesk.dto.TicketResponse;
import com.apipratudo.helpdesk.model.Message;
import com.apipratudo.helpdesk.model.Template;
import com.apipratudo.helpdesk.model.Ticket;
import com.apipratudo.helpdesk.service.HelpdeskService;
import com.apipratudo.helpdesk.service.IdempotencyService;
import com.apipratudo.helpdesk.service.WhatsappWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/helpdesk")
public class HelpdeskController {

  private final HelpdeskService helpdeskService;
  private final IdempotencyService idempotencyService;
  private final WhatsappWebhookService whatsappWebhookService;

  public HelpdeskController(
      HelpdeskService helpdeskService,
      IdempotencyService idempotencyService,
      WhatsappWebhookService whatsappWebhookService
  ) {
    this.helpdeskService = helpdeskService;
    this.idempotencyService = idempotencyService;
    this.whatsappWebhookService = whatsappWebhookService;
  }

  @GetMapping("/tickets")
  public ResponseEntity<List<TicketResponse>> listTickets(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestParam(name = "status", required = false) String status
  ) {
    List<TicketResponse> tickets = helpdeskService.listTickets(tenantId, status).stream()
        .map(this::mapTicket)
        .collect(Collectors.toList());
    return ResponseEntity.ok(tickets);
  }

  @PostMapping("/tickets")
  public ResponseEntity<?> createTicket(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody TicketCreateRequest request
  ) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyService.IdempotencyResult result = idempotencyService.execute(
          tenantId, "POST:/internal/helpdesk/tickets", idempotencyKey,
          () -> mapTicket(helpdeskService.createTicket(tenantId, request))
      );
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    TicketResponse created = mapTicket(helpdeskService.createTicket(tenantId, request));
    return ResponseEntity.ok(created);
  }

  @GetMapping("/tickets/{ticketId}")
  public ResponseEntity<TicketResponse> getTicket(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @PathVariable String ticketId
  ) {
    TicketResponse ticket = mapTicket(helpdeskService.getTicket(tenantId, ticketId));
    return ResponseEntity.ok(ticket);
  }

  @GetMapping("/tickets/{ticketId}/mensagens")
  public ResponseEntity<List<MessageResponse>> listMessages(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @PathVariable String ticketId
  ) {
    List<MessageResponse> messages = helpdeskService.listMessages(tenantId, ticketId).stream()
        .map(this::mapMessage)
        .collect(Collectors.toList());
    return ResponseEntity.ok(messages);
  }

  @PostMapping("/tickets/{ticketId}/mensagens")
  public ResponseEntity<?> createMessage(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @PathVariable String ticketId,
      @Valid @RequestBody MessageCreateRequest request
  ) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyService.IdempotencyResult result = idempotencyService.execute(
          tenantId, "POST:/internal/helpdesk/tickets/{ticketId}/mensagens", idempotencyKey,
          () -> mapMessage(helpdeskService.addMessage(tenantId, ticketId, request))
      );
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    MessageResponse message = mapMessage(helpdeskService.addMessage(tenantId, ticketId, request));
    return ResponseEntity.ok(message);
  }

  @PostMapping("/tickets/{ticketId}/atribuir")
  public ResponseEntity<?> assignTicket(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @PathVariable String ticketId,
      @Valid @RequestBody AssignRequest request
  ) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyService.IdempotencyResult result = idempotencyService.execute(
          tenantId, "POST:/internal/helpdesk/tickets/{ticketId}/atribuir", idempotencyKey,
          () -> mapTicket(helpdeskService.assign(tenantId, ticketId, request))
      );
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    TicketResponse ticket = mapTicket(helpdeskService.assign(tenantId, ticketId, request));
    return ResponseEntity.ok(ticket);
  }

  @PostMapping("/tickets/{ticketId}/status")
  public ResponseEntity<?> updateStatus(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @PathVariable String ticketId,
      @Valid @RequestBody StatusUpdateRequest request
  ) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyService.IdempotencyResult result = idempotencyService.execute(
          tenantId, "POST:/internal/helpdesk/tickets/{ticketId}/status", idempotencyKey,
          () -> mapTicket(helpdeskService.updateStatus(tenantId, ticketId, request))
      );
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    TicketResponse ticket = mapTicket(helpdeskService.updateStatus(tenantId, ticketId, request));
    return ResponseEntity.ok(ticket);
  }

  @GetMapping("/templates")
  public ResponseEntity<List<TemplateResponse>> listTemplates(
      @RequestHeader("X-Tenant-Id") String tenantId
  ) {
    List<TemplateResponse> templates = helpdeskService.listTemplates(tenantId).stream()
        .map(this::mapTemplate)
        .collect(Collectors.toList());
    return ResponseEntity.ok(templates);
  }

  @PostMapping("/templates")
  public ResponseEntity<?> createTemplate(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody TemplateCreateRequest request
  ) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyService.IdempotencyResult result = idempotencyService.execute(
          tenantId, "POST:/internal/helpdesk/templates", idempotencyKey,
          () -> mapTemplate(helpdeskService.createTemplate(tenantId, request))
      );
      return ResponseEntity.status(result.statusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(result.body());
    }
    TemplateResponse template = mapTemplate(helpdeskService.createTemplate(tenantId, request));
    return ResponseEntity.ok(template);
  }

  @GetMapping("/webhook/whatsapp")
  public ResponseEntity<String> verifyWebhook(
      @RequestParam(name = "hub.mode", required = false) String mode,
      @RequestParam(name = "hub.verify_token", required = false) String token,
      @RequestParam(name = "hub.challenge", required = false) String challenge
  ) {
    if ("subscribe".equals(mode) && whatsappWebhookService.verifyToken(token)) {
      return ResponseEntity.ok(challenge);
    }
    return ResponseEntity.status(403).body("forbidden");
  }

  @PostMapping(value = "/webhook/whatsapp", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> receiveWebhook(
      HttpServletRequest request,
      @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
      @RequestBody String rawBody
  ) {
    if (!whatsappWebhookService.validateSignature(rawBody, signature)) {
      return ResponseEntity.status(401).body(new com.apipratudo.helpdesk.error.ErrorResponse(
          "UNAUTHORIZED", "Invalid webhook signature", List.of(), null
      ));
    }
    whatsappWebhookService.handleWebhook(rawBody);
    return ResponseEntity.ok(java.util.Map.of("ok", true));
  }

  private TicketResponse mapTicket(Ticket ticket) {
    return new TicketResponse(
        ticket.id(),
        ticket.status().name(),
        ticket.assigneeUserId(),
        ticket.customerWaId(),
        ticket.createdAt(),
        ticket.updatedAt(),
        ticket.slaFirstResponseDueAt(),
        ticket.firstResponseAt(),
        ticket.autoSummary(),
        ticket.intentTags()
    );
  }

  private MessageResponse mapMessage(Message message) {
    return new MessageResponse(
        message.id(),
        message.ticketId(),
        message.direction().name(),
        message.text(),
        message.createdAt(),
        message.providerMessageId()
    );
  }

  private TemplateResponse mapTemplate(Template template) {
    return new TemplateResponse(
        template.id(),
        template.name(),
        template.body(),
        template.createdAt()
    );
  }
}
