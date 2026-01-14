package com.apipratudo.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalApiKeyGatewayTest {

  private static MockWebServer quotaServer;
  private static MockWebServer webhookServer;

  @Autowired
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    if (quotaServer == null) {
      quotaServer = new MockWebServer();
      quotaServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          return new MockResponse()
              .setResponseCode(200)
              .setHeader("Content-Type", "application/json")
              .setBody("{\"allowed\":true,\"limit\":100,\"remaining\":99}");
        }
      });
      try {
        quotaServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start quota mock server", e);
      }
    }

    if (webhookServer == null) {
      webhookServer = new MockWebServer();
      webhookServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          if ("GET".equals(request.getMethod()) && request.getPath() != null
              && request.getPath().startsWith("/v1/webhooks")) {
            return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"items\":[],\"nextCursor\":null}");
          }
          return new MockResponse().setResponseCode(404);
        }
      });
      try {
        webhookServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start webhook mock server", e);
      }
    }

    registry.add("quota.base-url", () -> quotaServer.url("/").toString());
    registry.add("webhook.base-url", () -> webhookServer.url("/").toString());
    registry.add("webhook.service-token", () -> "test-service");
  }

  @AfterAll
  static void shutdownServers() throws IOException {
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
    if (webhookServer != null) {
      webhookServer.shutdown();
    }
  }

  @Test
  void whTestKeyIsAcceptedInLocalProfile() throws Exception {
    mockMvc.perform(get("/v1/webhooks")
            .header("X-Api-Key", "wh-test"))
        .andExpect(status().isOk());
  }
}
