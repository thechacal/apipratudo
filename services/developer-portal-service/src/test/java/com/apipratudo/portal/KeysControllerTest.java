package com.apipratudo.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
class KeysControllerTest {

  private static MockWebServer quotaServer;
  private static MockWebServer billingServer;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @DynamicPropertySource
  static void registerClients(DynamicPropertyRegistry registry) {
    if (quotaServer == null) {
      quotaServer = new MockWebServer();
      quotaServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          if ("POST".equals(request.getMethod()) && "/v1/internal/keys/create-free".equals(request.getPath())) {
            return new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"1\",\"apiKey\":\"key-123\",\"ownerEmail\":\"teste@example.com\",\"orgName\":\"Acme\",\"plan\":\"FREE\",\"limits\":{\"requestsPerMinute\":30,\"requestsPerDay\":200},\"createdAt\":\"2024-01-01T00:00:00Z\"}");
          }
          return new MockResponse().setResponseCode(404);
        }
      });
      try {
        quotaServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start quota mock server", e);
      }
    }
    if (billingServer == null) {
      billingServer = new MockWebServer();
      billingServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          if ("POST".equals(request.getMethod()) && "/v1/billing/pix/charges".equals(request.getPath())) {
            return new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"chargeId\":\"ch-1\",\"status\":\"PENDING\"}");
          }
          return new MockResponse().setResponseCode(404);
        }
      });
      try {
        billingServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start billing mock server", e);
      }
    }

    registry.add("app.quota.base-url", () -> quotaServer.url("/").toString());
    registry.add("app.quota.portal-token", () -> "test-portal");
    registry.add("app.billing.base-url", () -> billingServer.url("/").toString());
    registry.add("app.billing.service-token", () -> "test-billing");
  }

  @AfterAll
  static void shutdownServers() throws IOException {
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
    if (billingServer != null) {
      billingServer.shutdown();
    }
  }

  @Test
  void requestKeyReturnsPortalResponse() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "email", "teste@example.com",
        "org", "Acme",
        "useCase", "tests"
    ));

    mockMvc.perform(post("/v1/keys/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.apiKey").value("key-123"))
        .andExpect(jsonPath("$.plan").value("FREE"));

    RecordedRequest request = quotaServer.takeRequest();
    assertThat(request.getHeader("X-Portal-Token")).isEqualTo("test-portal");
  }

  @Test
  void upgradeProxiesBilling() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("plan", "PREMIUM"));

    mockMvc.perform(post("/v1/keys/upgrade")
            .header("X-Api-Key", "key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.chargeId").value("ch-1"));

    RecordedRequest request = billingServer.takeRequest();
    assertThat(request.getHeader("X-Service-Token")).isEqualTo("test-billing");
  }
}
