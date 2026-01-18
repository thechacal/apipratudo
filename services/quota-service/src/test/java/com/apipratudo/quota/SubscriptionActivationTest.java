package com.apipratudo.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class SubscriptionActivationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void activatePremiumUpdatesPlanAndLimits() throws Exception {
    String apiKey = createApiKey(5, 10);

    String body = objectMapper.writeValueAsString(Map.of(
        "apiKey", apiKey,
        "plan", "PREMIUM"
    ));

    mockMvc.perform(post("/v1/subscriptions/activate-premium")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Internal-Token", "test-internal")
            .content(body))
        .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(get("/v1/quota/status")
            .header("X-Api-Key", apiKey))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("plan").asText()).isEqualTo("PREMIUM");
    assertThat(json.get("limits").get("requestsPerMinute").asInt()).isEqualTo(600);
    assertThat(json.get("limits").get("requestsPerDay").asInt()).isEqualTo(50000);
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
}
