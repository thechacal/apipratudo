package com.apipratudo.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(QuotaStatusWindowTest.FixedClockConfig.class)
class QuotaStatusWindowTest {

  private static final Instant FIXED_NOW = Instant.parse("2024-02-01T10:15:30Z");
  private static final Instant EXPECTED_MINUTE_RESET = Instant.parse("2024-02-01T10:16:00Z");
  private static final Instant EXPECTED_DAY_RESET = Instant.parse("2024-02-02T00:00:00Z");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void statusUsesSameWindowAsConsume() throws Exception {
    String apiKey = createApiKey(5, 10);
    for (int i = 0; i < 5; i++) {
      consume(apiKey, "req-" + i);
    }

    JsonNode status = quotaStatus(apiKey);
    JsonNode minute = status.get("usage").get("minute");
    JsonNode day = status.get("usage").get("day");

    assertThat(minute.get("used").asLong()).isEqualTo(5);
    assertThat(minute.get("remaining").asLong()).isEqualTo(0);
    assertThat(minute.get("resetAt").asText()).isEqualTo(EXPECTED_MINUTE_RESET.toString());

    assertThat(day.get("used").asLong()).isEqualTo(5);
    assertThat(day.get("remaining").asLong()).isEqualTo(5);
    assertThat(day.get("resetAt").asText()).isEqualTo(EXPECTED_DAY_RESET.toString());
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
            .contentType("application/json")
            .header("X-Admin-Token", "test-admin")
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("apiKey").asText();
  }

  private void consume(String apiKey, String requestId) throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "apiKey", apiKey,
        "requestId", requestId,
        "route", "GET /v1/webhooks",
        "cost", 1
    ));

    mockMvc.perform(post("/v1/quota/consume")
            .contentType("application/json")
            .header("X-Internal-Token", "test-internal")
            .content(body))
        .andExpect(status().isOk());
  }

  private JsonNode quotaStatus(String apiKey) throws Exception {
    MvcResult result = mockMvc.perform(get("/v1/quota/status")
            .header("X-Api-Key", apiKey))
        .andExpect(status().isOk())
        .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(QuotaStatusWindowTest.FIXED_NOW, ZoneOffset.UTC);
    }
  }
}
