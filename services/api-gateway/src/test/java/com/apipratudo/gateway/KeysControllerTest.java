package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
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

  private static MockWebServer portalServer;
  private static final AtomicInteger requestStatus = new AtomicInteger(201);
  private static final AtomicReference<String> requestBody = new AtomicReference<>(
      "{\"apiKey\":\"key-123\",\"plan\":\"FREE\",\"limits\":{\"requestsPerMinute\":30,\"requestsPerDay\":200}}\n");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @DynamicPropertySource
  static void registerPortalProperties(DynamicPropertyRegistry registry) {
    if (portalServer == null) {
      portalServer = new MockWebServer();
      portalServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          if ("POST".equals(request.getMethod()) && "/v1/keys/request".equals(request.getPath())) {
            return new MockResponse()
                .setResponseCode(requestStatus.get())
                .setHeader("Content-Type", "application/json")
                .setBody(requestBody.get());
          }
          if ("GET".equals(request.getMethod()) && "/v1/keys/status".equals(request.getPath())) {
            return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"plan\":\"FREE\"}");
          }
          return new MockResponse().setResponseCode(404);
        }
      });
      try {
        portalServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start portal mock server", e);
      }
    }
    registry.add("portal.base-url", () -> portalServer.url("/").toString());
    registry.add("portal.timeout-ms", () -> 2000);
  }

  @AfterAll
  static void shutdownServer() throws IOException {
    if (portalServer != null) {
      portalServer.shutdown();
    }
  }

  @Test
  void requestKeyIsPublic() throws Exception {
    requestStatus.set(201);
    requestBody.set("{\"apiKey\":\"key-123\",\"plan\":\"FREE\",\"limits\":{\"requestsPerMinute\":30,"
        + "\"requestsPerDay\":200}}\n");
    String body = objectMapper.writeValueAsString(Map.of(
        "email", "teste@example.com",
        "org", "Acme",
        "useCase", "tests"
    ));

    mockMvc.perform(post("/v1/keys/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.apiKey").value("key-123"));

    RecordedRequest request = portalServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getPath()).isEqualTo("/v1/keys/request");
    assertThat(request.getMethod()).isEqualTo("POST");
  }

  @Test
  void requestKeyMapsPortalFailureToServiceUnavailable() throws Exception {
    requestStatus.set(401);
    requestBody.set("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid X-Portal-Token\"}");

    String body = objectMapper.writeValueAsString(Map.of(
        "email", "teste@example.com",
        "org", "Acme",
        "useCase", "tests"
    ));

    mockMvc.perform(post("/v1/keys/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("PORTAL_UNAVAILABLE"));
  }

  @Test
  void statusRequiresApiKey() throws Exception {
    mockMvc.perform(get("/v1/keys/status"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  @Test
  void statusProxiesPortal() throws Exception {
    mockMvc.perform(get("/v1/keys/status")
            .header("X-Api-Key", "key-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plan").value("FREE"));

    RecordedRequest request = portalServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getPath()).isEqualTo("/v1/keys/status");
    assertThat(request.getHeader("X-Api-Key")).isEqualTo("key-123");
  }
}
