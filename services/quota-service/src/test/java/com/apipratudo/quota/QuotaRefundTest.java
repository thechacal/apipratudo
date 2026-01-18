package com.apipratudo.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class QuotaRefundTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void refundIsIdempotent() throws Exception {
    String apiKey = createApiKey(2, 2);
    String requestId = "req-" + UUID.randomUUID();

    consume(apiKey, requestId)
        .andExpect(status().isOk());

    refund(apiKey, requestId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.refunded").value(true));

    refund(apiKey, requestId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.refunded").value(true));

    JsonNode status = quotaStatus(apiKey);
    assertThat(status.get("usage").get("minute").get("used").asLong()).isEqualTo(0);
    assertThat(status.get("usage").get("day").get("used").asLong()).isEqualTo(0);
  }

  @Test
  void refundWithoutConsumptionIsNoOp() throws Exception {
    String apiKey = createApiKey(1, 1);
    String requestId = "req-" + UUID.randomUUID();

    refund(apiKey, requestId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.refunded").value(false));

    JsonNode status = quotaStatus(apiKey);
    assertThat(status.get("usage").get("minute").get("used").asLong()).isEqualTo(0);
    assertThat(status.get("usage").get("day").get("used").asLong()).isEqualTo(0);
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

  private org.springframework.test.web.servlet.ResultActions refund(String apiKey, String requestId) throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "apiKey", apiKey,
        "requestId", requestId
    ));

    return mockMvc.perform(post("/v1/quota/refund")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Internal-Token", "test-internal")
        .content(body));
  }

  private JsonNode quotaStatus(String apiKey) throws Exception {
    MvcResult result = mockMvc.perform(get("/v1/quota/status")
            .header("X-Api-Key", apiKey))
        .andExpect(status().isOk())
        .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString());
  }
}
