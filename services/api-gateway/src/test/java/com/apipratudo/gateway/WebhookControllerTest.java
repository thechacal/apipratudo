package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void createWebhookReturnsCreated() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    mockMvc.perform(post("/v1/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.status").value("created"));
  }

  @Test
  void createWebhookValidationError() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "eventType", "invoice.paid"
    ));

    mockMvc.perform(post("/v1/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://apipratudo.local/errors/validation"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.instance").value("/v1/webhooks"));
  }

  @Test
  void createWebhookIdempotency() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    MvcResult first = mockMvc.perform(post("/v1/webhooks")
            .header("Idempotency-Key", "abc-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    MvcResult second = mockMvc.perform(post("/v1/webhooks")
            .header("Idempotency-Key", "abc-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
    JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());

    assertThat(firstJson.get("id").asText()).isEqualTo(secondJson.get("id").asText());
  }

  @Test
  void createWebhookIdempotencyConflict() throws Exception {
    String baseBody = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    String changedBody = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.failed"
    ));

    mockMvc.perform(post("/v1/webhooks")
            .header("Idempotency-Key", "conflict-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(baseBody))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/v1/webhooks")
            .header("Idempotency-Key", "conflict-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(changedBody))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://apipratudo.local/errors/idempotency-conflict"))
        .andExpect(jsonPath("$.status").value(409));
  }
}
