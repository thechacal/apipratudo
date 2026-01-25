package com.apipratudo.billingsaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apipratudo.billingsaas.model.Charge;
import com.apipratudo.billingsaas.model.PixProviderIndex;
import com.apipratudo.billingsaas.repository.ChargeStore;
import com.apipratudo.billingsaas.repository.PixProviderIndexStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
class BillingSaasControllerTest {

  private static final String SERVICE_TOKEN = "test-service-token";
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ChargeStore chargeStore;

  @Autowired
  private PixProviderIndexStore pixProviderIndexStore;

  private String tenantId;

  @BeforeEach
  void setUp() {
    tenantId = "tenant-" + System.nanoTime();
  }

  @Test
  void createCustomerIdempotency() throws Exception {
    String payload = "{" +
        "\"name\":\"Cliente Teste\"," +
        "\"document\":\"12345678900\"," +
        "\"email\":\"teste@exemplo.com\"," +
        "\"phone\":\"+5511999999999\"" +
        "}";

    MvcResult first = mockMvc.perform(post("/internal/clientes")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", tenantId)
            .header("Idempotency-Key", "idem-1")
            .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andReturn();

    String firstId = readJson(first).get("id").asText();

    mockMvc.perform(post("/internal/clientes")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", tenantId)
            .header("Idempotency-Key", "idem-1")
            .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(firstId));
  }

  @Test
  void idempotencyConflictReturns409() throws Exception {
    String payload = "{" +
        "\"name\":\"Cliente A\"," +
        "\"document\":\"12345678900\"," +
        "\"email\":\"a@exemplo.com\"," +
        "\"phone\":\"+5511999999999\"" +
        "}";

    String payload2 = "{" +
        "\"name\":\"Cliente B\"," +
        "\"document\":\"12345678900\"," +
        "\"email\":\"b@exemplo.com\"," +
        "\"phone\":\"+5511999999999\"" +
        "}";

    mockMvc.perform(post("/internal/clientes")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", tenantId)
            .header("Idempotency-Key", "idem-conflict")
            .content(payload))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/internal/clientes")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", tenantId)
            .header("Idempotency-Key", "idem-conflict")
            .content(payload2))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));
  }

  @Test
  void chargePixWebhookAndRecurrence() throws Exception {
    String customerId = createCustomer(tenantId);

    String chargePayload = "{" +
        "\"customerId\":\"" + customerId + "\"," +
        "\"amountCents\":1990," +
        "\"currency\":\"BRL\"," +
        "\"description\":\"Assinatura\"," +
        "\"dueDate\":\"2026-01-31\"," +
        "\"recurrence\":{\"frequency\":\"MONTHLY\",\"interval\":1}" +
        "}";

    MvcResult chargeResult = mockMvc.perform(post("/internal/cobrancas")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", tenantId)
            .header("Idempotency-Key", "charge-1")
            .content(chargePayload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andReturn();

    String chargeId = readJson(chargeResult).get("id").asText();

    String pixPayload = "{" +
        "\"chargeId\":\"" + chargeId + "\"," +
        "\"expiresInSeconds\":3600" +
        "}";

    MvcResult pixResult = mockMvc.perform(post("/internal/pix/gerar")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", tenantId)
            .header("Idempotency-Key", "pix-1")
            .content(pixPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PIX_GENERATED"))
        .andExpect(jsonPath("$.pix.providerChargeId").isNotEmpty())
        .andReturn();

    String providerChargeId = readJson(pixResult).path("pix").path("providerChargeId").asText();

    String webhookPayload = "{" +
        "\"provider\":\"FAKE\"," +
        "\"providerChargeId\":\"" + providerChargeId + "\"," +
        "\"event\":\"PAID\"" +
        "}";

    mockMvc.perform(post("/internal/pix/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Webhook-Secret", "test-webhook-secret")
            .content(webhookPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    mockMvc.perform(post("/internal/pix/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Webhook-Secret", "test-webhook-secret")
            .content(webhookPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    mockMvc.perform(get("/internal/cobrancas/{id}/status", chargeId)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", tenantId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));

    List<Charge> charges = chargeStore.findByCreatedAtBetween(
        tenantId,
        Instant.EPOCH,
        Instant.now().plusSeconds(3600)
    );
    assertThat(charges).hasSize(2);

    Charge nextCharge = charges.stream()
        .filter(charge -> !charge.id().equals(chargeId))
        .findFirst()
        .orElseThrow();
    assertThat(nextCharge.dueDate()).isEqualTo(LocalDate.of(2026, 2, 28));

    mockMvc.perform(post("/internal/pix/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Webhook-Secret", "wrong-secret")
            .content(webhookPayload))
        .andExpect(status().isUnauthorized());

    PixProviderIndex index = pixProviderIndexStore.findByProviderChargeId(providerChargeId).orElseThrow();
    assertThat(index.tenantId()).isEqualTo(tenantId);
    assertThat(index.chargeId()).isEqualTo(chargeId);

    mockMvc.perform(get("/internal/relatorios")
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", tenantId)
            .param("from", LocalDate.now().minusDays(1).toString())
            .param("to", LocalDate.now().plusDays(1).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.countTotal").value(2))
        .andExpect(jsonPath("$.countPaid").value(1))
        .andExpect(jsonPath("$.countPending").value(1));
  }

  @Test
  void webhookDoesNotCrossTenant() throws Exception {
    String customerA = createCustomer(tenantId);

    MvcResult chargeResult = mockMvc.perform(post("/internal/cobrancas")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", tenantId)
            .header("Idempotency-Key", "charge-a")
            .content("{\"customerId\":\"" + customerA + "\",\"amountCents\":2500,\"currency\":\"BRL\",\"description\":\"Plano A\",\"dueDate\":\"2026-01-20\"}"))
        .andExpect(status().isCreated())
        .andReturn();

    String chargeId = readJson(chargeResult).get("id").asText();

    MvcResult pixResult = mockMvc.perform(post("/internal/pix/gerar")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", tenantId)
            .header("Idempotency-Key", "pix-a")
            .content("{\"chargeId\":\"" + chargeId + "\",\"expiresInSeconds\":3600}"))
        .andExpect(status().isOk())
        .andReturn();

    String providerChargeId = readJson(pixResult).path("pix").path("providerChargeId").asText();

    String otherTenant = "tenant-other-" + System.nanoTime();
    String customerB = createCustomer(otherTenant);

    MvcResult otherCharge = mockMvc.perform(post("/internal/cobrancas")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", otherTenant)
            .header("Idempotency-Key", "charge-b")
            .content("{\"customerId\":\"" + customerB + "\",\"amountCents\":3000,\"currency\":\"BRL\",\"description\":\"Plano B\",\"dueDate\":\"2026-01-25\"}"))
        .andExpect(status().isCreated())
        .andReturn();

    String otherChargeId = readJson(otherCharge).get("id").asText();

    mockMvc.perform(post("/internal/pix/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Webhook-Secret", "test-webhook-secret")
            .content("{\"provider\":\"FAKE\",\"providerChargeId\":\"" + providerChargeId + "\",\"event\":\"PAID\"}"))
        .andExpect(status().isOk());

    mockMvc.perform(get("/internal/cobrancas/{id}/status", otherChargeId)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", otherTenant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CREATED"));
  }

  @Test
  void webhookWithoutSecretIsUnauthorized() throws Exception {
    mockMvc.perform(post("/internal/pix/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"provider\":\"FAKE\",\"providerChargeId\":\"fake\",\"event\":\"PAID\"}"))
        .andExpect(status().isUnauthorized());
  }

  private String createCustomer(String tenant) throws Exception {
    String payload = "{" +
        "\"name\":\"Cliente Teste\"," +
        "\"document\":\"12345678900\"," +
        "\"email\":\"teste@exemplo.com\"," +
        "\"phone\":\"+5511999999999\"" +
        "}";

    MvcResult result = mockMvc.perform(post("/internal/clientes")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", SERVICE_TOKEN)
            .header("X-Tenant-Id", tenant)
            .header("Idempotency-Key", "cust-" + System.nanoTime())
            .content(payload))
        .andExpect(status().isCreated())
        .andReturn();

    return readJson(result).get("id").asText();
  }

  private JsonNode readJson(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }
}
