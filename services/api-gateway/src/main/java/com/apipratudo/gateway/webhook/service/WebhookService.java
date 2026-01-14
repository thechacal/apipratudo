package com.apipratudo.gateway.webhook.service;

import com.apipratudo.gateway.error.BadRequestException;
import com.apipratudo.gateway.error.ResourceNotFoundException;
import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.webhook.client.WebhookClient;
import com.apipratudo.gateway.webhook.client.WebhookClientRawResult;
import com.apipratudo.gateway.webhook.client.WebhookClientResult;
import com.apipratudo.gateway.webhook.dto.DeliveryTestResponse;
import com.apipratudo.gateway.webhook.dto.WebhookCreateRequest;
import com.apipratudo.gateway.webhook.dto.WebhookCreateResponse;
import com.apipratudo.gateway.webhook.dto.WebhookResponse;
import com.apipratudo.gateway.webhook.dto.WebhookUpdateRequest;
import com.apipratudo.gateway.webhook.model.Webhook;
import com.apipratudo.gateway.webhook.model.WebhookStatus;
import com.apipratudo.gateway.webhook.repo.WebhookRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WebhookService {

  private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

  private final WebhookRepository webhookRepository;
  private final DeliveryService deliveryService;
  private final WebhookClient webhookClient;
  private final Clock clock;

  public WebhookService(
      WebhookRepository webhookRepository,
      DeliveryService deliveryService,
      WebhookClient webhookClient,
      Clock clock
  ) {
    this.webhookRepository = webhookRepository;
    this.deliveryService = deliveryService;
    this.webhookClient = webhookClient;
    this.clock = clock;
  }

  public WebhookCreateResult create(String apiKey, WebhookCreateRequest request, String idempotencyKey) {
    List<String> events = normalizeEvents(request);
    WebhookClientResult result = webhookClient.create(
        apiKey,
        idempotencyKey,
        request.targetUrl(),
        events,
        request.secret(),
        traceId()
    );
    WebhookCreateResponse response = result.response();
    saveLocalCopy(response, events);
    log.info("Webhook created id={} enabled={} traceId={}", response.id(), response.enabled(), traceId());
    return new WebhookCreateResult(result.statusCode(), response);
  }

  public WebhookProxyResult list(String apiKey, String limit, String cursor) {
    WebhookClientRawResult result = webhookClient.list(apiKey, limit, cursor, traceId());
    return new WebhookProxyResult(result.statusCode(), result.body());
  }

  public WebhookProxyResult get(String apiKey, String id) {
    webhookRepository.findById(id)
        .filter(webhook -> webhook.status() == WebhookStatus.DELETED)
        .ifPresent(webhook -> {
          throw new ResourceNotFoundException("Webhook not found: " + id);
        });
    WebhookClientRawResult result = webhookClient.get(apiKey, id, traceId());
    return new WebhookProxyResult(result.statusCode(), result.body());
  }

  public WebhookResponse update(String id, WebhookUpdateRequest request) {
    Webhook existing = requireWebhook(id);
    if (request.status() == WebhookStatus.DELETED) {
      throw new BadRequestException("status cannot be DELETED", List.of("status cannot be DELETED"));
    }

    String targetUrl = request.targetUrl() != null ? request.targetUrl() : existing.targetUrl();
    String eventType = request.eventType() != null ? request.eventType() : existing.eventType();
    WebhookStatus status = request.status() != null ? request.status() : existing.status();

    Webhook updated = new Webhook(
        existing.id(),
        targetUrl,
        eventType,
        status,
        existing.createdAt(),
        Instant.now(clock)
    );

    webhookRepository.save(updated);
    log.info("Webhook updated id={} status={} traceId={}", updated.id(), updated.status(), traceId());
    return toResponse(updated);
  }

  public void delete(String id) {
    Webhook existing = requireWebhook(id);
    Webhook deleted = new Webhook(
        existing.id(),
        existing.targetUrl(),
        existing.eventType(),
        WebhookStatus.DELETED,
        existing.createdAt(),
        Instant.now(clock)
    );
    webhookRepository.save(deleted);
    log.info("Webhook deleted id={} status={} traceId={}", existing.id(), deleted.status(), traceId());
  }

  public DeliveryTestResponse test(String apiKey, String id) {
    Webhook webhook = requireWebhook(id);
    DeliveryTestResponse response = deliveryService.createTestDelivery(apiKey, webhook);
    log.info("Webhook test delivery created deliveryId={} webhookId={} status={} traceId={}",
        response.deliveryId(), webhook.id(), response.status(), traceId());
    return response;
  }

  private void saveLocalCopy(WebhookCreateResponse response, List<String> events) {
    Instant createdAt = response.createdAt() != null ? response.createdAt() : Instant.now(clock);
    Instant updatedAt = response.updatedAt() != null ? response.updatedAt() : createdAt;
    String eventType = !events.isEmpty() ? events.get(0) : null;
    if (response.events() != null && !response.events().isEmpty()) {
      eventType = response.events().get(0);
    }
    WebhookStatus status = response.enabled() ? WebhookStatus.ACTIVE : WebhookStatus.DISABLED;
    Webhook webhook = new Webhook(
        response.id(),
        response.targetUrl(),
        eventType,
        status,
        createdAt,
        updatedAt
    );
    webhookRepository.save(webhook);
  }

  private WebhookResponse toResponse(Webhook webhook) {
    return new WebhookResponse(
        webhook.id(),
        webhook.targetUrl(),
        webhook.eventType(),
        webhook.status(),
        webhook.createdAt(),
        webhook.updatedAt()
    );
  }

  private Webhook requireWebhook(String id) {
    Webhook webhook = webhookRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + id));
    if (webhook.status() == WebhookStatus.DELETED) {
      throw new ResourceNotFoundException("Webhook not found: " + id);
    }
    return webhook;
  }

  public record WebhookCreateResult(int statusCode, WebhookCreateResponse response) {
  }

  public record WebhookProxyResult(int statusCode, String body) {
  }

  private List<String> normalizeEvents(WebhookCreateRequest request) {
    List<String> events = request.events();
    String eventType = normalizeText(request.eventType());

    if (events != null && !events.isEmpty()) {
      List<String> normalized = new ArrayList<>();
      for (String event : events) {
        if (!StringUtils.hasText(event)) {
          throw new BadRequestException("events must not be blank", List.of("events must not be blank"));
        }
        normalized.add(event.trim());
      }
      if (StringUtils.hasText(eventType) && !normalized.contains(eventType)) {
        throw new BadRequestException("eventType must match events", List.of("eventType must be included in events"));
      }
      return normalized;
    }

    if (!StringUtils.hasText(eventType)) {
      throw new BadRequestException("events or eventType is required",
          List.of("events or eventType is required"));
    }

    return List.of(eventType);
  }

  private String normalizeText(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private String traceId() {
    String traceId = TraceIdUtils.currentTraceId();
    return traceId == null ? "-" : traceId;
  }
}
