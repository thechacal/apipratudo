package com.apipratudo.webhook.controller;

import com.apipratudo.webhook.dto.WebhookEventRequest;
import com.apipratudo.webhook.service.WebhookEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/events")
public class InternalEventController {

  private final WebhookEventService eventService;

  public InternalEventController(WebhookEventService eventService) {
    this.eventService = eventService;
  }

  @PostMapping
  public ResponseEntity<Void> publish(@Valid @RequestBody WebhookEventRequest request) {
    eventService.enqueueEvent(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }
}
