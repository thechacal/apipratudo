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
class IdentityVerifyProxyControllerTest {

  private static MockWebServer quotaServer;
  private static MockWebServer identityServer;

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

    if (identityServer == null) {
      identityServer = new MockWebServer();
      identityServer.setDispatcher(new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
          if ("POST".equals(request.getMethod()) && "/internal/v1/documentos/validar".equals(request.getPath())) {
            return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"tipo\":\"CPF\",\"documento_normalizado\":\"52998224725\",\"valido_estrutural\":true,\"motivos\":[]}");
          }
          return new MockResponse().setResponseCode(404);
        }
      });
      try {
        identityServer.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start identity mock server", e);
      }
    }

    registry.add("quota.base-url", () -> quotaServer.url("/").toString());
    registry.add("quota.internal-token", () -> "test-internal");
    registry.add("identity.base-url", () -> identityServer.url("/").toString());
    registry.add("identity.service-token", () -> "test-identity-token");
  }

  @AfterAll
  static void shutdownServers() throws IOException {
    if (quotaServer != null) {
      quotaServer.shutdown();
    }
    if (identityServer != null) {
      identityServer.shutdown();
    }
  }

  @Test
  void validarDocumentoSendsTenantIdAndInternalToken() throws Exception {
    mockMvc.perform(post("/v1/documentos/validar")
            .header("X-Api-Key", "test-key")
            .header("Idempotency-Key", "idem-doc-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tipo\":\"CPF\",\"documento\":\"529.982.247-25\"}"))
        .andExpect(status().isOk());

    RecordedRequest recorded = identityServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(recorded).isNotNull();
    assertThat(recorded.getHeader("X-Tenant-Id")).isEqualTo(HashingUtils.sha256Hex("test-key"));
    assertThat(recorded.getHeader("X-Service-Token")).isEqualTo("test-identity-token");
    assertThat(recorded.getHeader("Idempotency-Key")).isEqualTo("idem-doc-1");
    assertThat(recorded.getHeader("X-Request-Id")).isNotBlank();
  }
}
