package com.apipratudo.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void missingApiKeyReturns401() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks",
        "events", List.of("invoice.paid")
    ));

    mockMvc.perform(post("/v1/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  @Test
  void validationErrorForInvalidPayload() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "invalid-url",
        "events", List.of()
    ));

    mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void listRequiresApiKey() throws Exception {
    mockMvc.perform(get("/v1/webhooks"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  @Test
  void idempotencyReturnsSameWebhook() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks",
        "events", List.of("invoice.paid"),
        "secret", "shh"
    ));

    MvcResult first = mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .header("Idempotency-Key", "idem-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    MvcResult second = mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "test-key")
            .header("Idempotency-Key", "idem-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
    JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());

    assertThat(firstJson.get("id").asText()).isEqualTo(secondJson.get("id").asText());
    assertThat(firstJson.get("events").get(0).asText()).isEqualTo("invoice.paid");
  }

  @Test
  void listReturnsOnlyOwnedWebhooks() throws Exception {
    String ownerKey = "key-a-" + java.util.UUID.randomUUID();
    String otherKey = "key-b-" + java.util.UUID.randomUUID();
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks",
        "events", List.of("invoice.paid")
    ));

    mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", ownerKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", otherKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());

    MvcResult result = mockMvc.perform(get("/v1/webhooks")
            .header("X-Api-Key", ownerKey))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(response.get("items").size()).isEqualTo(1);
  }

  @Test
  void getReturns404ForWrongOwner() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks",
        "events", List.of("invoice.paid")
    ));

    MvcResult created = mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "key-a")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
    String id = json.get("id").asText();

    mockMvc.perform(get("/v1/webhooks/{id}", id)
            .header("X-Api-Key", "key-b"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }
}
