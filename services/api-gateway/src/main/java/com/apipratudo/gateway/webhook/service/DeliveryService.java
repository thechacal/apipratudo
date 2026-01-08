package com.apipratudo.gateway.webhook.service;

import com.apipratudo.gateway.error.ResourceNotFoundException;
import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.webhook.dto.DeliveryListResponse;
import com.apipratudo.gateway.webhook.dto.DeliveryResponse;
import com.apipratudo.gateway.webhook.model.Delivery;
import com.apipratudo.gateway.webhook.model.DeliveryStatus;
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
  private final Clock clock;

  public DeliveryService(DeliveryRepository deliveryRepository, Clock clock) {
    this.deliveryRepository = deliveryRepository;
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

  public DeliveryResponse retry(String id) {
    Delivery existing = requireDelivery(id);
    Delivery retried = new Delivery(
        UUID.randomUUID().toString(),
        existing.webhookId(),
        existing.eventType(),
        existing.targetUrl(),
        DeliveryStatus.SUCCESS,
        existing.attempt() + 1,
        200,
        Instant.now(clock)
    );

    deliveryRepository.save(retried);
    log.info("Delivery retried oldId={} newId={} status={} traceId={}", existing.id(), retried.id(), retried.status(),
        traceId());
    return toResponse(retried);
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
        delivery.status().name(),
        delivery.attempt(),
        delivery.responseCode(),
        delivery.createdAt()
    );
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
