package com.apipratudo.quota;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuotaControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void invalidApiKeyReturns401() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "apiKey", "invalid",
        "requestId", "req-" + UUID.randomUUID(),
        "route", "GET /v1/webhooks",
        "cost", 1
    ));

    mockMvc.perform(post("/v1/quota/consume")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Internal-Token", "test-internal")
            .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.allowed").value(false))
        .andExpect(jsonPath("$.reason").value("INVALID_KEY"));
  }

  @Test
  void quotaExceededReturns402() throws Exception {
    String apiKey = createApiKey(1, 1);

    consume(apiKey, "req-" + UUID.randomUUID())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(true));

    consume(apiKey, "req-" + UUID.randomUUID())
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.allowed").value(false))
        .andExpect(jsonPath("$.reason").value("QUOTA_EXCEEDED"))
        .andExpect(jsonPath("$.error").value("QUOTA_EXCEEDED"));
  }

  @Test
  void idempotencyPreventsDoubleCount() throws Exception {
    String apiKey = createApiKey(1, 1);
    String requestId = "req-" + UUID.randomUUID();

    consume(apiKey, requestId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(true));

    consume(apiKey, requestId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(true));

    consume(apiKey, "req-" + UUID.randomUUID())
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.allowed").value(false))
        .andExpect(jsonPath("$.reason").value("QUOTA_EXCEEDED"))
        .andExpect(jsonPath("$.error").value("QUOTA_EXCEEDED"));
  }

  private String createApiKey(int requestsPerMinute, int requestsPerDay) throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "name", "client-" + UUID.randomUUID(),
        "owner", "owner-" + UUID.randomUUID(),
        "limits", Map.of(
            "requestsPerMinute", requestsPerMinute,
            "requestsPerDay", requestsPerDay
        )
    ));

    MvcResult result = mockMvc.perform(post("/v1/api-keys")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Admin-Token", "test-admin")
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.apiKey").isNotEmpty())
        .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("apiKey").asText();
  }

  private org.springframework.test.web.servlet.ResultActions consume(String apiKey, String requestId) throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "apiKey", apiKey,
        "requestId", requestId,
        "route", "GET /v1/webhooks",
        "cost", 1
    ));

    return mockMvc.perform(post("/v1/quota/consume")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Internal-Token", "test-internal")
        .content(body));
  }
}
