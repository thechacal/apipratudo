package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.gateway.config.DeliveryProperties;
import com.apipratudo.gateway.webhook.model.Delivery;
import com.apipratudo.gateway.webhook.model.DeliveryStatus;
import com.apipratudo.gateway.webhook.repo.InMemoryDeliveryRepository;
import com.apipratudo.gateway.webhook.service.DeliveryDispatcher;
import com.apipratudo.gateway.webhook.service.DeliveryRetryPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.web.reactive.function.client.WebClient;

class DeliveryDispatcherTest {

  private MockWebServer server;
  private InMemoryDeliveryRepository repository;
  private DeliveryProperties properties;
  private CapturingTaskScheduler scheduler;
  private Clock clock;
  private DeliveryDispatcher dispatcher;

  @BeforeEach
  void setup() throws Exception {
    server = new MockWebServer();
    server.start();
    repository = new InMemoryDeliveryRepository();
    properties = new DeliveryProperties();
    properties.setMaxAttempts(3);
    properties.setInitialBackoffMs(100);
    properties.setMaxBackoffMs(1000);
    properties.setTimeoutMs(2000);
    clock = Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC);
    scheduler = new CapturingTaskScheduler(clock);

    DeliveryRetryPolicy retryPolicy = new DeliveryRetryPolicy(properties, clock);
    WebClient webClient = WebClient.builder().build();
    dispatcher = new DeliveryDispatcher(repository, retryPolicy, properties, webClient, scheduler, clock);
  }

  @AfterEach
  void teardown() throws Exception {
    server.shutdown();
  }

  @Test
  void dispatchSuccessDeliversWithoutRetry() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200));
    String targetUrl = server.url("/hook").toString();

    Delivery delivery = newDelivery(targetUrl);
    repository.save(delivery);

    dispatcher.dispatch(delivery, "trace-123");

    Delivery updated = repository.findById(delivery.id()).orElseThrow();
    assertThat(updated.status()).isEqualTo(DeliveryStatus.DELIVERED);
    assertThat(updated.attempt()).isEqualTo(1);
    assertThat(updated.responseCode()).isEqualTo(200);
    assertThat(updated.attempts()).hasSize(1);

    RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(recorded).isNotNull();
    assertThat(recorded.getHeader("X-Trace-Id")).isEqualTo("trace-123");
  }

  @Test
  void dispatchRetriesOnServerErrorUntilSuccess() {
    server.enqueue(new MockResponse().setResponseCode(500));
    server.enqueue(new MockResponse().setResponseCode(200));
    String targetUrl = server.url("/hook").toString();

    Delivery delivery = newDelivery(targetUrl);
    repository.save(delivery);

    dispatcher.dispatch(delivery, "trace-retry");

    Delivery updated = repository.findById(delivery.id()).orElseThrow();
    assertThat(updated.status()).isEqualTo(DeliveryStatus.DELIVERED);
    assertThat(updated.attempt()).isEqualTo(2);
    assertThat(updated.attempts()).hasSize(2);
  }

  @Test
  void dispatchDoesNotRetryOnClientError() {
    server.enqueue(new MockResponse().setResponseCode(400));
    String targetUrl = server.url("/hook").toString();

    Delivery delivery = newDelivery(targetUrl);
    repository.save(delivery);

    dispatcher.dispatch(delivery, "trace-client");

    Delivery updated = repository.findById(delivery.id()).orElseThrow();
    assertThat(updated.status()).isEqualTo(DeliveryStatus.FAILED);
    assertThat(updated.attempt()).isEqualTo(1);
    assertThat(updated.attempts()).hasSize(1);
  }

  @Test
  void dispatchRespectsRetryAfterHeader() {
    server.enqueue(new MockResponse()
        .setResponseCode(429)
        .addHeader("Retry-After", "2"));
    server.enqueue(new MockResponse().setResponseCode(200));
    String targetUrl = server.url("/hook").toString();

    Delivery delivery = newDelivery(targetUrl);
    repository.save(delivery);

    dispatcher.dispatch(delivery, "trace-429");

    List<Duration> delays = scheduler.getDelays();
    assertThat(delays).hasSizeGreaterThanOrEqualTo(2);
    assertThat(delays.get(1).toMillis()).isGreaterThanOrEqualTo(2000);
  }

  private Delivery newDelivery(String targetUrl) {
    return new Delivery(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "invoice.paid",
        targetUrl,
        DeliveryStatus.PENDING,
        1,
        0,
        Instant.now(clock),
        List.of()
    );
  }

  static class CapturingTaskScheduler implements TaskScheduler {
    private final Clock clock;
    private final List<Duration> delays = new ArrayList<>();

    CapturingTaskScheduler(Clock clock) {
      this.clock = clock;
    }

    List<Duration> getDelays() {
      return delays;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
      return schedule(task, Instant.now(clock));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
      Duration delay = Duration.between(Instant.now(clock), startTime);
      delays.add(delay.isNegative() ? Duration.ZERO : delay);
      task.run();
      return new CompletedScheduledFuture();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
      return schedule(task, startTime);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
      return schedule(task, Instant.now(clock));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
      return schedule(task, startTime);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
      return schedule(task, Instant.now(clock));
    }
  }

  static class CompletedScheduledFuture implements ScheduledFuture<Object> {
    @Override
    public long getDelay(TimeUnit unit) {
      return 0;
    }

    @Override
    public int compareTo(Delayed other) {
      return 0;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public Object get() {
      return null;
    }

    @Override
    public Object get(long timeout, TimeUnit unit) {
      return null;
    }
  }
}
