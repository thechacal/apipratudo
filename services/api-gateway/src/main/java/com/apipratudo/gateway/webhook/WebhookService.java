package com.apipratudo.gateway.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class WebhookService {

  private final ConcurrentMap<String, StoredResponse> idempotencyStore = new ConcurrentHashMap<>();

  public WebhookCreateResponse create(WebhookCreateRequest request, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return newResponse();
    }

    String key = idempotencyKey.trim();
    String payloadHash = hashPayload(request);

    StoredResponse stored = idempotencyStore.compute(key, (ignored, existing) -> {
      if (existing == null) {
        return new StoredResponse(payloadHash, newResponse());
      }
      if (!existing.payloadHash().equals(payloadHash)) {
        throw new IdempotencyConflictException(key);
      }
      return existing;
    });

    return stored.response();
  }

  private WebhookCreateResponse newResponse() {
    return new WebhookCreateResponse(UUID.randomUUID().toString(), "created");
  }

  private String hashPayload(WebhookCreateRequest request) {
    String payload = request.targetUrl() + "|" + request.eventType();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private record StoredResponse(String payloadHash, WebhookCreateResponse response) {
  }
}
