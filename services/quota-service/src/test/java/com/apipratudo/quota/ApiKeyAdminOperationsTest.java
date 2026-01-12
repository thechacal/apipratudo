package com.apipratudo.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ApiKeyAdminOperationsTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void rotateAndDisableApiKey() throws Exception {
    ApiKeyRecord record = createApiKey();

    MvcResult rotated = mockMvc.perform(post("/v1/api-keys/{id}/rotate", record.id())
            .header("X-Admin-Token", "test-admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.apiKey").isNotEmpty())
        .andReturn();

    String newKey = objectMapper.readTree(rotated.getResponse().getContentAsString()).get("apiKey").asText();

    consume(record.apiKey())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.reason").value("INVALID_KEY"));

    consume(newKey)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(true));

    String statusBody = objectMapper.writeValueAsString(Map.of("status", "DISABLED"));
    mockMvc.perform(patch("/v1/api-keys/{id}/status", record.id())
            .header("X-Admin-Token", "test-admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(statusBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"));

    consume(newKey)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.reason").value("INVALID_KEY"));
  }

  private ApiKeyRecord createApiKey() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "name", "client-" + UUID.randomUUID(),
        "owner", "owner-" + UUID.randomUUID(),
        "limits", Map.of(
            "requestsPerMinute", 10,
            "requestsPerDay", 100
        )
    ));

    MvcResult result = mockMvc.perform(post("/v1/api-keys")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Admin-Token", "test-admin")
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return new ApiKeyRecord(json.get("id").asText(), json.get("apiKey").asText());
  }

  private org.springframework.test.web.servlet.ResultActions consume(String apiKey) throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "apiKey", apiKey,
        "requestId", "req-" + UUID.randomUUID(),
        "route", "GET /v1/webhooks",
        "cost", 1
    ));

    return mockMvc.perform(post("/v1/quota/consume")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Internal-Token", "test-internal")
        .content(body));
  }

  private record ApiKeyRecord(String id, String apiKey) {
  }
}
