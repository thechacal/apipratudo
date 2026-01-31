package com.apipratudo.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.helpdesk.config.HelpdeskProperties;
import com.apipratudo.helpdesk.dto.MessageCreateRequest;
import com.apipratudo.helpdesk.dto.TicketCreateRequest;
import com.apipratudo.helpdesk.model.MessageDirection;
import com.apipratudo.helpdesk.repository.InMemoryHelpdeskStore;
import com.apipratudo.helpdesk.service.HelpdeskService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class HelpdeskServiceTest {

  @Test
  void createAndListTicket() {
    HelpdeskProperties properties = new HelpdeskProperties();
    properties.getIntentKeywords().put("problema", "SUPORTE");
    HelpdeskService service = new HelpdeskService(new InMemoryHelpdeskStore(), properties,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    var ticket = service.createTicket("tenant-1", new TicketCreateRequest("5588999990000"));
    assertThat(ticket.id()).startsWith("tkt_");
    assertThat(service.listTickets("tenant-1", null)).hasSize(1);

    service.addMessage("tenant-1", ticket.id(), new MessageCreateRequest(
        MessageDirection.INBOUND.name(), "Tenho um problema aqui", null));

    assertThat(service.listMessages("tenant-1", ticket.id())).hasSize(1);
    var refreshed = service.getTicket("tenant-1", ticket.id());
    assertThat(refreshed.intentTags()).contains("SUPORTE");
  }
}
