package com.apipratudo.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(QuotaRolloverTest.MutableClockConfig.class)
class QuotaRolloverTest {

  private static final Instant START = Instant.parse("2024-02-01T10:15:30Z");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private MutableClock clock;

  @Test
  void rolloverResetsMinuteAndDayWindows() throws Exception {
    clock.setInstant(START);
    String apiKey = createApiKey(2, 2);

    consume(apiKey, "req-1");

    JsonNode status = quotaStatus(apiKey);
    assertThat(status.get("usage").get("minute").get("used").asLong()).isEqualTo(1);
    assertThat(status.get("usage").get("day").get("used").asLong()).isEqualTo(1);

    clock.setInstant(START.plusSeconds(90));
    JsonNode afterMinute = quotaStatus(apiKey);
    assertThat(afterMinute.get("usage").get("minute").get("used").asLong()).isEqualTo(0);
    assertThat(afterMinute.get("usage").get("day").get("used").asLong()).isEqualTo(1);

    clock.setInstant(START.plusSeconds(86400 + 10));
    JsonNode afterDay = quotaStatus(apiKey);
    assertThat(afterDay.get("usage").get("day").get("used").asLong()).isEqualTo(0);
  }

  private void consume(String apiKey, String requestId) throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "apiKey", apiKey,
        "requestId", requestId,
        "route", "GET /v1/webhooks",
        "cost", 1
    ));

    mockMvc.perform(post("/v1/quota/consume")
            .contentType(MediaType.APPLICATION_JSON)
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

  static class MutableClock extends Clock {
    private final AtomicReference<Instant> instantRef;
    private final ZoneId zone;

    MutableClock(Instant instant, ZoneId zone) {
      this.instantRef = new AtomicReference<>(instant);
      this.zone = zone;
    }

    void setInstant(Instant instant) {
      instantRef.set(instant);
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new MutableClock(instantRef.get(), zone);
    }

    @Override
    public Instant instant() {
      return instantRef.get();
    }
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class MutableClockConfig {

    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(START, ZoneOffset.UTC);
    }
  }
}
