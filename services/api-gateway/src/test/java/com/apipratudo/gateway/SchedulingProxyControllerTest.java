package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apipratudo.gateway.idempotency.HashingUtils;
import java.io.IOException;
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
class SchedulingProxyControllerTest {

  private static MockWebServer quotaServer;
  private static MockWebServer schedulingServer;

  @Autowired
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    if (quotaServer == null) {
      quotaServer = new MockWebServer();
      quotaServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          String path = request.getPath();
          if (path != null && path.startsWith("/v1/quota/consume")) {
            return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"allowed\":true,\"limit\":100,\"remaining\":99}");
          }
          if (path != null && path.startsWith("/v1/quota/refund")) {
            return new MockResponse().setResponseCode(200);
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

    if (schedulingServer == null) {
      schedulingServer = new MockWebServer();
      schedulingServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          if ("POST".equals(request.getMethod()) && "/v1/servicos".equals(request.getPath())) {
            return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"srv_1\",\"name\":\"Corte\",\"durationMin\":40,\"prepMin\":5,"
                    + "\"bufferMin\":10,\"noShowFeeCents\":2000,\"active\":true,"
                    + "\"createdAt\":\"2026-01-26T10:00:00Z\"}");
          }
          return new MockResponse().setResponseCode(404);
        }
      });
      try {
        schedulingServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start scheduling mock server", e);
      }
    }

    registry.add("quota.base-url", () -> quotaServer.url("/").toString());
    registry.add("quota.internal-token", () -> "test-internal");
    registry.add("scheduling.base-url", () -> schedulingServer.url("/").toString());
    registry.add("scheduling.service-token", () -> "test-scheduling-token");
  }

  @AfterAll
  static void shutdownServers() throws IOException {
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
    if (schedulingServer != null) {
      schedulingServer.shutdown();
    }
  }

  @Test
  void createServiceSendsTenantId() throws Exception {
    mockMvc.perform(post("/v1/servicos")
            .header("X-Api-Key", "test-key")
            .header("Idempotency-Key", "idem-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Corte\",\"durationMin\":40,\"prepMin\":5,\"bufferMin\":10,"
                + "\"noShowFeeCents\":2000,\"active\":true}"))
        .andExpect(status().isOk());

    RecordedRequest recorded = schedulingServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(recorded).isNotNull();
    assertThat(recorded.getHeader("X-Tenant-Id")).isEqualTo(HashingUtils.sha256Hex("test-key"));
    assertThat(recorded.getHeader("Idempotency-Key")).isEqualTo("idem-1");
    assertThat(recorded.getHeader("X-Request-Id")).isNotBlank();
    assertThat(recorded.getHeader("X-Service-Token")).isEqualTo("test-scheduling-token");
  }
}
