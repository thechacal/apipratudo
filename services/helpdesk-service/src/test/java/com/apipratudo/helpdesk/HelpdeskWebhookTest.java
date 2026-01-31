package com.apipratudo.helpdesk;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
class HelpdeskWebhookTest {

  private static final String TOKEN = "test-token";
  private static final String SECRET = "test-secret";

  @Autowired
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("app.security.service-token", () -> "test-service-token");
    registry.add("app.whatsapp.verify-token", () -> TOKEN);
    registry.add("app.whatsapp.app-secret", () -> SECRET);
    registry.add("app.firestore.enabled", () -> false);
  }

  @Test
  void verifyWebhookReturnsChallenge() throws Exception {
    mockMvc.perform(get("/internal/helpdesk/webhook/whatsapp")
            .header("X-Service-Token", "test-service-token")
            .param("hub.mode", "subscribe")
            .param("hub.verify_token", TOKEN)
            .param("hub.challenge", "abc"))
        .andExpect(status().isOk());
  }

  @Test
  void invalidVerifyTokenReturns403() throws Exception {
    mockMvc.perform(get("/internal/helpdesk/webhook/whatsapp")
            .header("X-Service-Token", "test-service-token")
            .param("hub.mode", "subscribe")
            .param("hub.verify_token", "wrong")
            .param("hub.challenge", "abc"))
        .andExpect(status().isForbidden());
  }

  @Test
  void webhookSignatureValidated() throws Exception {
    String body = "{\"entry\":[]}";
    String signature = "sha256=" + hmacSha256Hex(SECRET, body);

    mockMvc.perform(post("/internal/helpdesk/webhook/whatsapp")
            .header("X-Service-Token", "test-service-token")
            .header("X-Hub-Signature-256", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));
  }

  private String hmacSha256Hex(String secret, String payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder();
    for (byte b : digest) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }
}
