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
class DuplasenaResultsProxyControllerTest {

  private static MockWebServer quotaServer;
  private static MockWebServer duplasenaServer;

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

    if (duplasenaServer == null) {
      duplasenaServer = new MockWebServer();
      duplasenaServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          if ("GET".equals(request.getMethod())
              && "/v1/duplasena/resultado-oficial".equals(request.getPath())) {
            return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")

                .setBody("{" +
                    "\"fonte\":\"CAIXA\"," +
                    "\"loteria\":\"DUPLASENA\"," +
                    "\"concurso\":\"1234\"," +
                    "\"dataApuracao\":\"2025-08-13\"," +
                    "\"sorteio1\":[\"01\",\"02\",\"03\",\"04\",\"05\",\"06\"]," +
                    "\"sorteio2\":[\"07\",\"08\",\"09\",\"10\",\"11\",\"12\"]," +
                    "\"capturadoEm\":\"2026-01-15T00:11:55Z\"" +
                    "}");
          }
          return new MockResponse().setResponseCode(404);
        }
      });
      try {
        duplasenaServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start duplasena mock server", e);
      }
    }

    registry.add("quota.base-url", () -> quotaServer.url("/").toString());
    registry.add("quota.internal-token", () -> "test-internal");
    registry.add("duplasena.base-url", () -> duplasenaServer.url("/").toString());
  }

  @AfterAll
  static void shutdownServers() throws IOException {
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
    if (duplasenaServer != null) {
      duplasenaServer.shutdown();
    }
  }

  @Test
  void proxyRetornaResultado() throws Exception {
    mockMvc.perform(get("/v1/duplasena/resultado-oficial")
            .header("X-Api-Key", "test-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loteria").value("DUPLASENA"))
        .andExpect(jsonPath("$.sorteio1.length()").value(6))
        .andExpect(jsonPath("$.sorteio2.length()").value(6));

    RecordedRequest recorded = duplasenaServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(recorded).isNotNull();
    assertThat(recorded.getMethod()).isEqualTo("GET");
    assertThat(recorded.getPath()).isEqualTo("/v1/duplasena/resultado-oficial");
  }
}
