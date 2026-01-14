package com.apipratudo.gateway.webhook.service;

import com.apipratudo.gateway.logging.TraceIdUtils;
import com.apipratudo.gateway.webhook.client.WebhookClient;
import com.apipratudo.gateway.webhook.client.WebhookEventData;
import com.apipratudo.gateway.webhook.client.WebhookEventRequest;
import com.apipratudo.gateway.webhook.dto.DeliveryStatus;
import com.apipratudo.gateway.webhook.model.Delivery;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WebhookEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(WebhookEventPublisher.class);

  private final WebhookClient webhookClient;

  public WebhookEventPublisher(WebhookClient webhookClient) {
    this.webhookClient = webhookClient;
  }

  public void publishDeliveryCreated(String apiKey, Delivery delivery, DeliveryStatus publicStatus) {
    if (!StringUtils.hasText(apiKey) || delivery == null) {
      return;
    }
    WebhookEventRequest event = new WebhookEventRequest(
        "delivery.created",
        apiKey,
        new WebhookEventData(delivery.id(), publicStatus.name(), delivery.createdAt()),
        Instant.now()
    );

    try {
      webhookClient.publishEvent(event, traceId());
      log.info("Webhook event published event=delivery.created deliveryId={} apiKey={}",
          delivery.id(), apiKey);
    } catch (Exception ex) {
      log.warn("Webhook event publish failed deliveryId={} error={}", delivery.id(), ex.getMessage());
    }
  }

  private String traceId() {
    String traceId = TraceIdUtils.currentTraceId();
    return traceId == null ? "-" : traceId;
  }
}
