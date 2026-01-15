package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuperseteResultsProxyControllerTest {

  private static MockWebServer quotaServer;
  private static MockWebServer superseteServer;

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

    if (superseteServer == null) {
      superseteServer = new MockWebServer();
      superseteServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          if ("GET".equals(request.getMethod())
              && "/v1/supersete/resultado-oficial".equals(request.getPath())) {
            return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")

                .setBody("{" +
                    "\"fonte\":\"CAIXA\"," +
                    "\"loteria\":\"SUPERSETE\"," +
                    "\"concurso\":\"1234\"," +
                    "\"dataApuracao\":\"2025-08-13\"," +
                    "\"colunas\":[\"0\",\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]," +
                    "\"capturadoEm\":\"2026-01-15T00:11:55Z\"" +
                    "}");
          }
          return new MockResponse().setResponseCode(404);
        }
      });
      try {
        superseteServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start supersete mock server", e);
      }
    }

    registry.add("quota.base-url", () -> quotaServer.url("/").toString());
    registry.add("quota.internal-token", () -> "test-internal");
    registry.add("supersete.base-url", () -> superseteServer.url("/").toString());
  }

  @AfterAll
  static void shutdownServers() throws IOException {
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
    if (superseteServer != null) {
      superseteServer.shutdown();
    }
  }

  @Test
  void proxyRetornaResultado() throws Exception {
    mockMvc.perform(get("/v1/supersete/resultado-oficial")
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loteria").value("SUPERSETE"))
        .andExpect(jsonPath("$.colunas.length()").value(7));

    RecordedRequest recorded = superseteServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(recorded).isNotNull();
    assertThat(recorded.getMethod()).isEqualTo("GET");
    assertThat(recorded.getPath()).isEqualTo("/v1/supersete/resultado-oficial");
  }
}
