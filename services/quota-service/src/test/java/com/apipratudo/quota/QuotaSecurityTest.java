package com.apipratudo.quota;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuotaSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void adminEndpointsRequireToken() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "name", "client",
        "owner", "owner",
        "limits", Map.of(
            "requestsPerMinute", 10,
            "requestsPerDay", 100
        )
    ));

    mockMvc.perform(post("/v1/api-keys")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  @Test
  void internalEndpointsRequireToken() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "apiKey", "invalid",
        "requestId", "req-1",
        "route", "GET /v1/webhooks",
        "cost", 1
    ));

    mockMvc.perform(post("/v1/quota/consume")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  @Test
  void statusRequiresAdminOrInternalToken() throws Exception {
    mockMvc.perform(get("/v1/quota/status")
            .param("apiKey", "any"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }
}
