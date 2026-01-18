package com.apipratudo.quota;

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
class FreeKeyCreationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void createFreeKeyUsesPlanDefaultsAndEnforcesLimits() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "email", "teste@example.com",
        "org", "Acme",
        "useCase", "tests"
    ));

    mockMvc.perform(post("/v1/internal/keys/create-free")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Portal-Token", "test-portal")
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.apiKey").isNotEmpty())
        .andExpect(jsonPath("$.plan").value("FREE"))
        .andExpect(jsonPath("$.limits.requestsPerMinute").value(30))
        .andExpect(jsonPath("$.limits.requestsPerDay").value(200))
        .andExpect(jsonPath("$.credits.remaining").value(0));

    mockMvc.perform(post("/v1/internal/keys/create-free")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Portal-Token", "test-portal")
            .content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("KEY_CREATION_LIMIT"));
  }
}
