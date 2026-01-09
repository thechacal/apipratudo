package com.apipratudo.gateway.webhook.service;

import com.apipratudo.gateway.error.BadRequestException;
import com.apipratudo.gateway.error.ResourceNotFoundException;
import com.apipratudo.gateway.idempotency.HashingUtils;
import com.apipratudo.gateway.idempotency.IdempotencyRequest;
import com.apipratudo.gateway.idempotency.IdempotencyResponse;
import com.apipratudo.gateway.idempotency.IdempotencyResult;
import com.apipratudo.gateway.idempotency.IdempotencyStore;
import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.webhook.dto.DeliveryTestResponse;
import com.apipratudo.gateway.webhook.dto.WebhookCreateRequest;
import com.apipratudo.gateway.webhook.dto.WebhookCreateResponse;
import com.apipratudo.gateway.webhook.dto.WebhookListResponse;
import com.apipratudo.gateway.webhook.dto.WebhookResponse;
import com.apipratudo.gateway.webhook.dto.WebhookUpdateRequest;
import com.apipratudo.gateway.webhook.model.Webhook;
import com.apipratudo.gateway.webhook.model.WebhookStatus;
import com.apipratudo.gateway.webhook.repo.WebhookRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WebhookService {

  private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
  private static final String WEBHOOKS_PATH = "/v1/webhooks";
  private static final ObjectMapper HASH_MAPPER = new ObjectMapper()
      .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private final WebhookRepository webhookRepository;
  private final DeliveryService deliveryService;
  private final IdempotencyStore idempotencyStore;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public WebhookService(
      WebhookRepository webhookRepository,
      DeliveryService deliveryService,
      IdempotencyStore idempotencyStore,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.webhookRepository = webhookRepository;
    this.deliveryService = deliveryService;
    this.idempotencyStore = idempotencyStore;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public WebhookCreateResult create(WebhookCreateRequest request, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      Webhook created = createWebhook(request);
      WebhookCreateResponse response = toCreateResponse(created);
      log.info("Webhook created id={} status={} traceId={}", created.id(), created.status(), traceId());
      return new WebhookCreateResult(HttpStatus.CREATED.value(), response, false);
    }

    String requestHash = hashRequest(request);
    String key = idempotencyKey.trim();

    IdempotencyRequest idempotencyRequest = new IdempotencyRequest(
        "POST",
        WEBHOOKS_PATH,
        key,
        requestHash
    );

    Webhook created = newWebhook(UUID.randomUUID().toString(), request, Instant.now(clock));

    IdempotencyResult result = idempotencyStore.execute(idempotencyRequest, transaction -> {
      webhookRepository.save(created, transaction);
      WebhookCreateResponse response = toCreateResponse(created);
      return new IdempotencyResponse(
          HttpStatus.CREATED.value(),
          toJson(response),
          Collections.emptyMap()
      );
    });

    WebhookCreateResponse response = parseCreateResponse(result.responseBodyJson());
    if (result.replay()) {
      log.info("Webhook replayed id={} key={} traceId={}", response.id(), key, traceId());
    } else {
      log.info("Webhook created id={} status={} key={} traceId={}", response.id(), response.status(), key, traceId());
    }

    return new WebhookCreateResult(result.statusCode(), response, result.replay());
  }

  public WebhookListResponse list(int page, int size) {
    List<Webhook> filtered = webhookRepository.findAll().stream()
        .filter(webhook -> webhook.status() != WebhookStatus.DELETED)
        .sorted(Comparator.comparing(Webhook::createdAt).reversed().thenComparing(Webhook::id))
        .collect(Collectors.toList());

    long total = filtered.size();
    List<WebhookResponse> items = paginate(filtered, page, size).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());

    return new WebhookListResponse(items, page, size, total);
  }

  public WebhookResponse get(String id) {
    return toResponse(requireWebhook(id));
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

  public DeliveryTestResponse test(String id) {
    Webhook webhook = requireWebhook(id);
    DeliveryTestResponse response = deliveryService.createTestDelivery(webhook);
    log.info("Webhook test delivery created deliveryId={} webhookId={} status={} traceId={}",
        response.deliveryId(), webhook.id(), response.status(), traceId());
    return response;
  }

  private Webhook createWebhook(WebhookCreateRequest request) {
    Webhook webhook = newWebhook(UUID.randomUUID().toString(), request, Instant.now(clock));
    webhookRepository.save(webhook);
    return webhook;
  }

  private Webhook newWebhook(String id, WebhookCreateRequest request, Instant createdAt) {
    return new Webhook(
        id,
        request.targetUrl(),
        request.eventType(),
        WebhookStatus.ACTIVE,
        createdAt,
        createdAt
    );
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

  private WebhookCreateResponse toCreateResponse(Webhook webhook) {
    return new WebhookCreateResponse(webhook.id(), webhook.status());
  }

  private Webhook requireWebhook(String id) {
    Webhook webhook = webhookRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + id));
    if (webhook.status() == WebhookStatus.DELETED) {
      throw new ResourceNotFoundException("Webhook not found: " + id);
    }
    return webhook;
  }

  private String hashRequest(WebhookCreateRequest request) {
    try {
      JsonNode parsed = objectMapper.valueToTree(request);
      JsonNode normalized = normalizeJson(parsed);
      String canonicalJson = HASH_MAPPER.writeValueAsString(normalized);
      return HashingUtils.sha256Hex(canonicalJson);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to hash request", e);
    }
  }

  private JsonNode normalizeJson(JsonNode node) {
    if (node == null || node.isNull() || node.isValueNode()) {
      return node;
    }
    if (node.isArray()) {
      ArrayNode arrayNode = HASH_MAPPER.createArrayNode();
      for (JsonNode child : node) {
        arrayNode.add(normalizeJson(child));
      }
      return arrayNode;
    }
    if (node.isObject()) {
      ObjectNode objectNode = HASH_MAPPER.createObjectNode();
      TreeMap<String, JsonNode> sorted = new TreeMap<>();
      node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), normalizeJson(entry.getValue())));
      sorted.forEach(objectNode::set);
      return objectNode;
    }
    return node;
  }

  private String toJson(WebhookCreateResponse response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize response", e);
    }
  }

  private WebhookCreateResponse parseCreateResponse(String json) {
    try {
      return objectMapper.readValue(json, WebhookCreateResponse.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse stored response", e);
    }
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

  public record WebhookCreateResult(int statusCode, WebhookCreateResponse response, boolean replay) {
  }

  private String traceId() {
    String traceId = TraceIdUtils.currentTraceId();
    return traceId == null ? "-" : traceId;
  }
}
