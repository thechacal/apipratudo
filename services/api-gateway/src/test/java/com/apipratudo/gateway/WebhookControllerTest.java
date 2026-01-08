package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"app.firestore.enabled=false", "app.docs.redirect-enabled=true"})
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
        .andExpect(jsonPath("$.status").value("ACTIVE"));
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
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void createWebhookIdempotency() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    String key = "idempotency-" + UUID.randomUUID();

    MvcResult first = mockMvc.perform(post("/v1/webhooks")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    MvcResult second = mockMvc.perform(post("/v1/webhooks")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
    JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());

    assertThat(firstJson.get("id").asText()).isEqualTo(secondJson.get("id").asText());
    assertThat(firstJson.get("status").asText()).isEqualTo("ACTIVE");
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

    String key = "conflict-" + UUID.randomUUID();

    mockMvc.perform(post("/v1/webhooks")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(baseBody))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/v1/webhooks")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(changedBody))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error").value("CONFLICT"));
  }

  @Test
  void getWebhookNotFound() throws Exception {
    mockMvc.perform(get("/v1/webhooks/nao-existe"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void updateWebhookChangesFields() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    MvcResult created = mockMvc.perform(post("/v1/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
        .andExpect(status().isCreated())
        .andReturn();

    String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    String patchBody = objectMapper.writeValueAsString(Map.of(
        "status", "DISABLED",
        "eventType", "invoice.failed"
    ));

    mockMvc.perform(patch("/v1/webhooks/{id}", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("DISABLED"))
        .andExpect(jsonPath("$.eventType").value("invoice.failed"));
  }

  @Test
  void deleteWebhookMarksDeleted() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    MvcResult created = mockMvc.perform(post("/v1/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
        .andExpect(status().isCreated())
        .andReturn();

    String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc.perform(delete("/v1/webhooks/{id}", id))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/v1/webhooks/{id}", id))
        .andExpect(status().isNotFound());
  }

  @Test
  void testWebhookCreatesDelivery() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    MvcResult created = mockMvc.perform(post("/v1/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
        .andExpect(status().isCreated())
        .andReturn();

    String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc.perform(post("/v1/webhooks/{id}/test", id))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.deliveryId").isNotEmpty())
        .andExpect(jsonPath("$.status").value("SUCCESS"));
  }
}
