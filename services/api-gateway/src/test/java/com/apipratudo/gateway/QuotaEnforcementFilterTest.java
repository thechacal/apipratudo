package com.apipratudo.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuotaEnforcementFilterTest {

  private static MockWebServer quotaServer;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @DynamicPropertySource
  static void registerQuotaProperties(DynamicPropertyRegistry registry) {
    if (quotaServer == null) {
      quotaServer = new MockWebServer();
      try {
        quotaServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start quota mock server", e);
      }
    }
    registry.add("quota.base-url", () -> quotaServer.url("/").toString());
    registry.add("quota.timeout-ms", () -> 2000);
  }

  @AfterAll
  static void shutdownQuotaServer() throws IOException {
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
  }

  @Test
  void missingApiKeyReturns401() throws Exception {
    mockMvc.perform(get("/v1/webhooks"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  @Test
  void rateLimitedReturns429() throws Exception {
    quotaServer.enqueue(new MockResponse()
        .setResponseCode(429)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"allowed\":false,\"reason\":\"RATE_LIMITED\"}"));

    mockMvc.perform(get("/v1/webhooks")
            .header("X-Api-Key", "rate-key"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
  }

  @Test
  void allowedRequestPassesThrough() throws Exception {
    quotaServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"allowed\":true,\"limit\":100,\"remaining\":99}"));

    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    mockMvc.perform(post("/v1/webhooks")
            .header("X-Api-Key", "allow-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());
  }
}
