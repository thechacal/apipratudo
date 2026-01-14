package com.apipratudo.webhook.service;

import com.apipratudo.webhook.delivery.DeliveryOutboxRepository;
import com.apipratudo.webhook.delivery.OutboundDelivery;
import com.apipratudo.webhook.delivery.OutboundDeliveryStatus;
import com.apipratudo.webhook.dto.WebhookEventData;
import com.apipratudo.webhook.dto.WebhookEventRequest;
import com.apipratudo.webhook.model.Webhook;
import com.apipratudo.webhook.repository.WebhookRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WebhookEventService {

  private static final Logger log = LoggerFactory.getLogger(WebhookEventService.class);

  private final WebhookRepository webhookRepository;
  private final DeliveryOutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public WebhookEventService(
      WebhookRepository webhookRepository,
      DeliveryOutboxRepository outboxRepository,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.webhookRepository = webhookRepository;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public void enqueueEvent(WebhookEventRequest request) {
    Instant now = Instant.now(clock);
    String apiKey = request.apiKey().trim();
    String event = request.event().trim();
    Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : now;
    WebhookEventRequest payload = new WebhookEventRequest(
        event,
        apiKey,
        request.data(),
        occurredAt
    );

    String payloadJson = toJson(payload);
    List<Webhook> webhooks = listAllByApiKey(apiKey);
    int queued = 0;

    for (Webhook webhook : webhooks) {
      if (!webhook.enabled()) {
        continue;
      }
      if (!webhook.events().contains(event)) {
        continue;
      }
      OutboundDelivery delivery = new OutboundDelivery(
          UUID.randomUUID().toString(),
          webhook.id(),
          apiKey,
          deliveryId(request.data()),
          event,
          webhook.targetUrl(),
          webhook.secret(),
          payloadJson,
          OutboundDeliveryStatus.PENDING,
          0,
          now,
          null,
          null,
          now,
          now
      );
      outboxRepository.save(delivery);
      queued++;
    }

    log.info("Event queued event={} apiKey={} deliveries={} deliveryId={}",
        event,
        apiKey,
        queued,
        deliveryId(request.data()));
  }

  private String toJson(WebhookEventRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize webhook event", e);
    }
  }

  private String deliveryId(WebhookEventData data) {
    return data == null ? null : data.deliveryId();
  }

  private List<Webhook> listAllByApiKey(String apiKey) {
    List<Webhook> all = new ArrayList<>();
    String cursor = null;
    int limit = 200;
    while (true) {
      WebhookRepository.Page page = webhookRepository.listByApiKey(apiKey, limit, cursor);
      all.addAll(page.items());
      if (!StringUtils.hasText(page.nextCursor())) {
        break;
      }
      cursor = page.nextCursor();
    }
    return all;
  }
}
