package com.apipratudo.gateway.webhook.service;

import com.apipratudo.gateway.error.ResourceNotFoundException;
import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.webhook.dto.DeliveryListResponse;
import com.apipratudo.gateway.webhook.dto.DeliveryResponse;
import com.apipratudo.gateway.webhook.dto.DeliveryTestResponse;
import com.apipratudo.gateway.webhook.model.Delivery;
import com.apipratudo.gateway.webhook.model.DeliveryStatus;
import com.apipratudo.gateway.webhook.model.Webhook;
import com.apipratudo.gateway.webhook.repo.DeliveryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeliveryService {

  private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

  private final DeliveryRepository deliveryRepository;
  private final WebhookEventPublisher eventPublisher;
  private final Clock clock;

  public DeliveryService(
      DeliveryRepository deliveryRepository,
      WebhookEventPublisher eventPublisher,
      Clock clock
  ) {
    this.deliveryRepository = deliveryRepository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  public DeliveryListResponse list(String webhookId, int page, int size) {
    String filter = webhookId == null || webhookId.isBlank() ? null : webhookId.trim();

    List<Delivery> source = filter == null
        ? deliveryRepository.findAll()
        : deliveryRepository.findByWebhookId(filter);

    List<Delivery> filtered = source.stream()
        .sorted(Comparator.comparing(Delivery::createdAt).reversed().thenComparing(Delivery::id))
        .collect(Collectors.toList());

    long total = filtered.size();
    List<DeliveryResponse> items = paginate(filtered, page, size).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());

    return new DeliveryListResponse(items, page, size, total);
  }

  public DeliveryResponse get(String id) {
    return toResponse(requireDelivery(id));
  }

  public DeliveryResponse retry(String apiKey, String id) {
    Delivery existing = requireDelivery(id);
    Delivery retried = new Delivery(
        UUID.randomUUID().toString(),
        existing.webhookId(),
        existing.eventType(),
        existing.targetUrl(),
        DeliveryStatus.PENDING,
        existing.attempt() + 1,
        0,
        Instant.now(clock),
        List.of()
    );

    deliveryRepository.save(retried);
    eventPublisher.publishDeliveryCreated(apiKey, retried, publicStatus(retried.status()));
    log.info("Delivery retried oldId={} newId={} status={} traceId={}", existing.id(), retried.id(), retried.status(),
        traceId());
    return toResponse(retried);
  }

  public DeliveryTestResponse createTestDelivery(String apiKey, Webhook webhook) {
    Delivery delivery = new Delivery(
        UUID.randomUUID().toString(),
        webhook.id(),
        webhook.eventType(),
        webhook.targetUrl(),
        DeliveryStatus.PENDING,
        1,
        0,
        Instant.now(clock),
        List.of()
    );

    deliveryRepository.save(delivery);
    eventPublisher.publishDeliveryCreated(apiKey, delivery, publicStatus(delivery.status()));
    log.info("Delivery created deliveryId={} webhookId={} status={} traceId={}", delivery.id(), webhook.id(),
        delivery.status(), traceId());
    return new DeliveryTestResponse(delivery.id(), publicStatus(delivery.status()));
  }

  private Delivery requireDelivery(String id) {
    return deliveryRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + id));
  }

  private DeliveryResponse toResponse(Delivery delivery) {
    return new DeliveryResponse(
        delivery.id(),
        delivery.webhookId(),
        delivery.eventType(),
        delivery.targetUrl(),
        publicStatus(delivery.status()),
        delivery.attempt(),
        delivery.responseCode(),
        delivery.createdAt()
    );
  }

  private com.apipratudo.gateway.webhook.dto.DeliveryStatus publicStatus(DeliveryStatus status) {
    if (status == null) {
      return com.apipratudo.gateway.webhook.dto.DeliveryStatus.FAILED;
    }
    return switch (status) {
      case FAILED -> com.apipratudo.gateway.webhook.dto.DeliveryStatus.FAILED;
      case PENDING -> com.apipratudo.gateway.webhook.dto.DeliveryStatus.PENDING;
      case DELIVERED -> com.apipratudo.gateway.webhook.dto.DeliveryStatus.SUCCESS;
    };
  }

  private <T> List<T> paginate(List<T> items, int page, int size) {
    if (items.isEmpty()) {
      return List.of();
    }
    int fromIndex = Math.max((page - 1) * size, 0);
    if (fromIndex >= items.size()) {
      return List.of();
    }
    int toIndex = Math.min(fromIndex + size, items.size());
    return items.subList(fromIndex, toIndex);
  }

  private String traceId() {
    String traceId = TraceIdUtils.currentTraceId();
    return traceId == null ? "-" : traceId;
  }
}
