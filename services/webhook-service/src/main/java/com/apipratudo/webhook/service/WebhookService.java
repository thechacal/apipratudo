package com.apipratudo.webhook.service;

import com.apipratudo.webhook.dto.CreateWebhookRequest;
import com.apipratudo.webhook.dto.WebhookListResponse;
import com.apipratudo.webhook.dto.WebhookResponse;
import com.apipratudo.webhook.error.ResourceNotFoundException;
import com.apipratudo.webhook.model.Webhook;
import com.apipratudo.webhook.repository.WebhookRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WebhookService {

  private final WebhookRepository repository;
  private final Clock clock;

  public WebhookService(WebhookRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public WebhookResponse createWebhook(String apiKey, String idempotencyKey, CreateWebhookRequest request) {
    String normalizedKey = normalizeKey(idempotencyKey);
    if (StringUtils.hasText(normalizedKey)) {
      Optional<Webhook> existing = repository.findByApiKeyAndIdempotencyKey(apiKey, normalizedKey);
      if (existing.isPresent()) {
        return toResponse(existing.get());
      }
    }

    Instant now = Instant.now(clock);
    Webhook webhook = new Webhook(
        UUID.randomUUID().toString(),
        apiKey,
        request.targetUrl(),
        List.copyOf(request.events()),
        request.secret(),
        true,
        now,
        now,
        normalizedKey
    );

    repository.save(webhook);
    return toResponse(webhook);
  }

  public WebhookListResponse listWebhooks(String apiKey, int limit, String cursor) {
    WebhookRepository.Page page = repository.listByApiKey(apiKey, limit, cursor);
    List<WebhookResponse> items = page.items().stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
    return new WebhookListResponse(items, page.nextCursor());
  }

  public WebhookResponse getWebhook(String apiKey, String id) {
    Webhook webhook = repository.findById(id)
        .filter(found -> apiKey.equals(found.apiKey()))
        .orElseThrow(() -> new ResourceNotFoundException("Webhook not found"));
    return toResponse(webhook);
  }

  private String normalizeKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey)) {
      return null;
    }
    return idempotencyKey.trim();
  }

  private WebhookResponse toResponse(Webhook webhook) {
    return new WebhookResponse(
        webhook.id(),
        webhook.targetUrl(),
        webhook.events(),
        webhook.enabled(),
        webhook.createdAt(),
        webhook.updatedAt()
    );
  }
}
