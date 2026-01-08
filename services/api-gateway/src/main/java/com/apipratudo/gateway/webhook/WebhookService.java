package com.apipratudo.gateway.webhook;

import com.apipratudo.gateway.idempotency.HashingUtils;
import com.apipratudo.gateway.idempotency.IdempotencyRequest;
import com.apipratudo.gateway.idempotency.IdempotencyResponse;
import com.apipratudo.gateway.idempotency.IdempotencyResult;
import com.apipratudo.gateway.idempotency.IdempotencyStore;
import com.apipratudo.gateway.webhook.storage.WebhookRecord;
import com.apipratudo.gateway.webhook.storage.WebhookRepository;
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
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WebhookService {

  private static final String WEBHOOKS_PATH = "/v1/webhooks";
  private static final ObjectMapper HASH_MAPPER = new ObjectMapper()
      .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private final IdempotencyStore idempotencyStore;
  private final WebhookRepository webhookRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public WebhookService(
      IdempotencyStore idempotencyStore,
      WebhookRepository webhookRepository,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.idempotencyStore = idempotencyStore;
    this.webhookRepository = webhookRepository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public WebhookCreateResult create(WebhookCreateRequest request, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      WebhookRecord record = newRecord(UUID.randomUUID().toString(), request, Instant.now(clock));
      webhookRepository.save(record);
      return new WebhookCreateResult(HttpStatus.CREATED.value(), toResponse(record), false);
    }

    String requestHash = hashRequest(request);
    String key = idempotencyKey.trim();
    String webhookId = UUID.randomUUID().toString();
    Instant createdAt = Instant.now(clock);

    IdempotencyRequest idempotencyRequest = new IdempotencyRequest(
        "POST",
        WEBHOOKS_PATH,
        key,
        requestHash
    );

    WebhookRecord record = newRecord(webhookId, request, createdAt);

    IdempotencyResult result = idempotencyStore.execute(idempotencyRequest, transaction -> {
      webhookRepository.save(record, transaction);
      WebhookCreateResponse response = toResponse(record);
      return new IdempotencyResponse(
          HttpStatus.CREATED.value(),
          toJson(response),
          Collections.emptyMap()
      );
    });

    return new WebhookCreateResult(
        result.statusCode(),
        parseResponse(result.responseBodyJson()),
        result.replay()
    );
  }

  private WebhookRecord newRecord(String id, WebhookCreateRequest request, Instant createdAt) {
    return new WebhookRecord(
        id,
        request.targetUrl(),
        request.eventType(),
        "created",
        createdAt
    );
  }

  private WebhookCreateResponse toResponse(WebhookRecord record) {
    return new WebhookCreateResponse(record.id(), record.status());
  }

  private String hashRequest(WebhookCreateRequest request) {
    try {
      String rawJson = objectMapper.writeValueAsString(request);
      JsonNode parsed = HASH_MAPPER.readTree(rawJson);
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

  private WebhookCreateResponse parseResponse(String json) {
    try {
      return objectMapper.readValue(json, WebhookCreateResponse.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse stored response", e);
    }
  }

  public record WebhookCreateResult(int statusCode, WebhookCreateResponse response, boolean replay) {
  }
}
