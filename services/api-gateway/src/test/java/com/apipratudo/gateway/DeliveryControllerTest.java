package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeliveryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void listAndRetryDeliveries() throws Exception {
    String webhookId = createWebhook();
    String deliveryId = createDeliveryForWebhook(webhookId);

    MvcResult listResult = mockMvc.perform(get("/v1/deliveries")
            .param("webhookId", webhookId))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode listJson = objectMapper.readTree(listResult.getResponse().getContentAsString());
    boolean contains = false;
    for (JsonNode item : listJson.get("items")) {
      if (webhookId.equals(item.get("webhookId").asText())) {
        contains = true;
        break;
      }
    }
    assertThat(contains).isTrue();

    mockMvc.perform(get("/v1/deliveries/{id}", deliveryId))
        .andExpect(status().isOk());

    MvcResult retryResult = mockMvc.perform(post("/v1/deliveries/{id}/retry", deliveryId))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode retryJson = objectMapper.readTree(retryResult.getResponse().getContentAsString());
    assertThat(retryJson.get("id").asText()).isNotEqualTo(deliveryId);
    assertThat(retryJson.get("attempt").asInt()).isEqualTo(2);
  }

  @Test
  void getDeliveryNotFound() throws Exception {
    mockMvc.perform(get("/v1/deliveries/nao-existe"))
        .andExpect(status().isNotFound());
  }

  private String createWebhook() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "targetUrl", "https://cliente.exemplo.com/webhooks/apipratudo",
        "eventType", "invoice.paid"
    ));

    MvcResult result = mockMvc.perform(post("/v1/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("id").asText();
  }

  private String createDeliveryForWebhook(String webhookId) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/webhooks/{id}/test", webhookId))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("deliveryId").asText();
  }
}
