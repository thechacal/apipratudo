package com.apipratudo.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apipratudo.scheduling.client.BillingSaasClient;
import com.apipratudo.scheduling.client.WebhookEventClient;
import com.apipratudo.scheduling.config.AgendaCreditsProperties;
import com.apipratudo.scheduling.config.NoShowProperties;
import com.apipratudo.scheduling.config.SchedulingProperties;
import com.apipratudo.scheduling.dto.ReservationRequest;
import com.apipratudo.scheduling.dto.ServiceCreateRequest;
import com.apipratudo.scheduling.error.AgendaCreditsExceededException;
import com.apipratudo.scheduling.error.HoldExpiredException;
import com.apipratudo.scheduling.error.SlotUnavailableException;
import com.apipratudo.scheduling.idempotency.IdempotencyTransaction;
import com.apipratudo.scheduling.idempotency.IdempotencyResponse;
import com.apipratudo.scheduling.idempotency.IdempotencyResult;
import com.apipratudo.scheduling.idempotency.IdempotencyService;
import com.apipratudo.scheduling.idempotency.InMemoryIdempotencyStore;
import com.apipratudo.scheduling.model.AppointmentStatus;
import com.apipratudo.scheduling.model.Appointment;
import com.apipratudo.scheduling.model.Customer;
import com.apipratudo.scheduling.model.FineStatus;
import com.apipratudo.scheduling.model.ServiceDef;
import com.apipratudo.scheduling.repository.InMemorySchedulingStore;
import com.apipratudo.scheduling.service.SchedulingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SchedulingServiceTest {

  private static final class NoopTransaction implements IdempotencyTransaction {
    @Override
    public void set(String collection, String documentId, java.util.Map<String, Object> data) {
      // no-op
    }

    @Override
    public boolean isNoop() {
      return true;
    }
  }

  @Test
  void reserveConfirmAndDetectConflict() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-26T10:00:00Z"), ZoneOffset.UTC);
    SchedulingProperties properties = new SchedulingProperties();
    AgendaCreditsProperties creditsProperties = new AgendaCreditsProperties();
    creditsProperties.setDefaultCredits(1);
    NoShowProperties noShowProperties = new NoShowProperties();
    WebhookEventClient webhookClient = Mockito.mock(WebhookEventClient.class);
    SchedulingService service = new SchedulingService(
        new InMemorySchedulingStore(),
        properties,
        creditsProperties,
        noShowProperties,
        clock,
        webhookClient,
        Mockito.mock(BillingSaasClient.class)
    );

    ServiceDef created = service.createService(
        "tenant-1",
        new ServiceCreateRequest("Corte", 40, 5, 10, 2000, true),
        new NoopTransaction()
    );

    ReservationRequest request = new ReservationRequest(
        created.id(),
        "main",
        Instant.parse("2026-01-26T12:00:00Z"),
        new com.apipratudo.scheduling.dto.CustomerRequest("Ana", "+5588999990000", "ana@email.com"),
        "Preferencia"
    );

    var reserved = service.reserve("tenant-1", request, new NoopTransaction());
    assertThat(reserved.status()).isEqualTo(AppointmentStatus.HELD.name());

    var confirmed = service.confirm("tenant-1", reserved.appointmentId());
    assertThat(confirmed.status()).isEqualTo(AppointmentStatus.CONFIRMED.name());
    ArgumentCaptor<com.apipratudo.scheduling.client.WebhookEventRequest> captor =
        ArgumentCaptor.forClass(com.apipratudo.scheduling.client.WebhookEventRequest.class);
    Mockito.verify(webhookClient).publishEvent(captor.capture(), Mockito.isNull());
    assertThat(captor.getValue().event()).isEqualTo("SCHEDULING_APPOINTMENT_CONFIRMED");

    assertThatThrownBy(() -> service.reserve("tenant-1", request, new NoopTransaction()))
        .isInstanceOf(SlotUnavailableException.class);
  }

  @Test
  void confirmFailsWhenHoldExpired() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-26T10:00:00Z"), ZoneOffset.UTC);
    SchedulingProperties properties = new SchedulingProperties();
    AgendaCreditsProperties creditsProperties = new AgendaCreditsProperties();
    NoShowProperties noShowProperties = new NoShowProperties();
    InMemorySchedulingStore store = new InMemorySchedulingStore();
    SchedulingService service = new SchedulingService(
        store,
        properties,
        creditsProperties,
        noShowProperties,
        clock,
        Mockito.mock(WebhookEventClient.class),
        Mockito.mock(BillingSaasClient.class)
    );

    Appointment appointment = new Appointment(
        "apt_1",
        "tenant-1",
        "srv_1",
        "main",
        Instant.parse("2026-01-26T12:00:00Z"),
        Instant.parse("2026-01-26T12:55:00Z"),
        AppointmentStatus.HELD,
        Instant.parse("2026-01-26T09:59:00Z"),
        new Customer("Ana", "+5588999990000", "ana@email.com"),
        "Preferencia",
        2000,
        Instant.parse("2026-01-26T10:00:00Z"),
        Instant.parse("2026-01-26T10:00:00Z"),
        null,
        null,
        null,
        null
    );
    store.saveAppointment("tenant-1", appointment);

    assertThatThrownBy(() -> service.confirm("tenant-1", "apt_1"))
        .isInstanceOf(HoldExpiredException.class);
  }

  @Test
  void idempotencyReplaysSameResponse() {
    IdempotencyService idempotencyService = new IdempotencyService(
        new InMemoryIdempotencyStore(),
        new ObjectMapper()
    );
    AtomicInteger counter = new AtomicInteger();

    IdempotencyResult first = idempotencyService.execute(
        "tenant-1",
        "POST",
        "/v1/reservar",
        "idem-1",
        new ServiceCreateRequest("Corte", 40, 5, 10, 2000, true),
        tx -> {
          counter.incrementAndGet();
          return new IdempotencyResponse(200, "{\"ok\":true}", null);
        }
    );

    IdempotencyResult second = idempotencyService.execute(
        "tenant-1",
        "POST",
        "/v1/reservar",
        "idem-1",
        new ServiceCreateRequest("Corte", 40, 5, 10, 2000, true),
        tx -> {
          counter.incrementAndGet();
          return new IdempotencyResponse(200, "{\"ok\":true}", null);
        }
    );

    assertThat(counter.get()).isEqualTo(1);
    assertThat(first.responseBodyJson()).isEqualTo("{\"ok\":true}");
    assertThat(second.responseBodyJson()).isEqualTo("{\"ok\":true}");
    assertThat(second.replayed()).isTrue();
  }

  @Test
  void confirmConsumesAgendaCreditsAndBlocksWhenExhausted() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-26T10:00:00Z"), ZoneOffset.UTC);
    SchedulingProperties properties = new SchedulingProperties();
    AgendaCreditsProperties creditsProperties = new AgendaCreditsProperties();
    creditsProperties.setDefaultCredits(1);
    NoShowProperties noShowProperties = new NoShowProperties();
    SchedulingService service = new SchedulingService(
        new InMemorySchedulingStore(),
        properties,
        creditsProperties,
        noShowProperties,
        clock,
        Mockito.mock(WebhookEventClient.class),
        Mockito.mock(BillingSaasClient.class)
    );

    ServiceDef created = service.createService(
        "tenant-1",
        new ServiceCreateRequest("Corte", 40, 5, 10, 2000, true),
        new NoopTransaction()
    );

    ReservationRequest request = new ReservationRequest(
        created.id(),
        "main",
        Instant.parse("2026-01-26T12:00:00Z"),
        new com.apipratudo.scheduling.dto.CustomerRequest("Ana", "+5588999990000", "ana@email.com"),
        "Preferencia"
    );

    var reserved = service.reserve("tenant-1", request, new NoopTransaction());
    service.confirm("tenant-1", reserved.appointmentId());

    ReservationRequest secondRequest = new ReservationRequest(
        created.id(),
        "main",
        Instant.parse("2026-01-26T13:00:00Z"),
        new com.apipratudo.scheduling.dto.CustomerRequest("Ana", "+5588999990000", "ana@email.com"),
        "Preferencia"
    );

    var reserved2 = service.reserve("tenant-1", secondRequest, new NoopTransaction());
    assertThatThrownBy(() -> service.confirm("tenant-1", reserved2.appointmentId()))
        .isInstanceOf(AgendaCreditsExceededException.class);
  }

  @Test
  void processNoShowCreatesFine() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-26T12:30:00Z"), ZoneOffset.UTC);
    SchedulingProperties properties = new SchedulingProperties();
    AgendaCreditsProperties creditsProperties = new AgendaCreditsProperties();
    NoShowProperties noShowProperties = new NoShowProperties();
    noShowProperties.setGraceMin(15);
    InMemorySchedulingStore store = new InMemorySchedulingStore();
    SchedulingService service = new SchedulingService(
        store,
        properties,
        creditsProperties,
        noShowProperties,
        clock,
        Mockito.mock(WebhookEventClient.class),
        Mockito.mock(BillingSaasClient.class)
    );

    Appointment appointment = new Appointment(
        "apt_1",
        "tenant-1",
        "srv_1",
        "main",
        Instant.parse("2026-01-26T12:00:00Z"),
        Instant.parse("2026-01-26T12:55:00Z"),
        AppointmentStatus.CONFIRMED,
        Instant.parse("2026-01-26T11:00:00Z"),
        new Customer("Ana", "+5588999990000", "ana@email.com"),
        "Preferencia",
        2000,
        Instant.parse("2026-01-26T11:00:00Z"),
        Instant.parse("2026-01-26T11:00:00Z"),
        null,
        null,
        null,
        null
    );
    store.saveAppointment("tenant-1", appointment);
    store.ensureTenant("tenant-1");

    service.processNoShows("tenant-1");

    assertThat(store.listFines("tenant-1", "main", FineStatus.PENDING)).hasSize(1);
  }
}
