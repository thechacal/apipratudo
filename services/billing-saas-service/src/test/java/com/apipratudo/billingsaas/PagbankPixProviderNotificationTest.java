package com.apipratudo.billingsaas;

import com.apipratudo.billingsaas.config.CryptoProperties;
import com.apipratudo.billingsaas.config.PagBankProperties;
import com.apipratudo.billingsaas.crypto.CryptoService;
import com.apipratudo.billingsaas.model.Charge;
import com.apipratudo.billingsaas.model.ChargeStatus;
import com.apipratudo.billingsaas.model.Customer;
import com.apipratudo.billingsaas.model.EncryptedValue;
import com.apipratudo.billingsaas.model.PagbankEnvironment;
import com.apipratudo.billingsaas.model.PagbankProviderConfig;
import com.apipratudo.billingsaas.provider.PagbankPixProvider;
import com.apipratudo.billingsaas.repository.InMemoryPagbankProviderConfigStore;
import com.apipratudo.billingsaas.repository.PagbankProviderConfigStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class PagbankPixProviderNotificationTest {

  @Test
  void includesNotificationUrlInPayload() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>("");
    CountDownLatch latch = new CountDownLatch(1);

    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    int port = server.getAddress().getPort();

    server.createContext("/orders", exchange -> handleOrders(exchange, capturedBody, latch, port));
    server.createContext("/qrcode", exchange -> handleQrCode(exchange));
    server.start();

    try {
      ObjectMapper mapper = new ObjectMapper();
      PagBankProperties properties = new PagBankProperties();
      properties.setProductionBaseUrl("http://localhost:" + port);
      properties.setNotificationUrl("https://apipratudo.com/v1/pix/webhook");
      properties.setTimeoutMs(5000);

      CryptoProperties cryptoProperties = new CryptoProperties();
      cryptoProperties.setMasterKeyBase64(base64Key());
      CryptoService cryptoService = new CryptoService(cryptoProperties);

      String tenantId = "tenant-test";
      EncryptedValue token = cryptoService.encrypt("test-token", tenantId + ":PAGBANK");

      PagbankProviderConfigStore store = new InMemoryPagbankProviderConfigStore();
      PagbankProviderConfig config = new PagbankProviderConfig(
          tenantId,
          true,
          PagbankEnvironment.PRODUCTION,
          token,
          null,
          "fp",
          Instant.now(),
          Instant.now(),
          null
      );
      store.save(tenantId, config);

      PagbankPixProvider provider = new PagbankPixProvider(
          WebClient.builder(),
          mapper,
          properties,
          store,
          cryptoService,
          Clock.systemUTC()
      );

      Charge charge = new Charge(
          "chg_1",
          "cus_1",
          100,
          "BRL",
          "Teste",
          LocalDate.now(),
          null,
          null,
          ChargeStatus.CREATED,
          Instant.now(),
          Instant.now(),
          null,
          null,
          null,
          tenantId
      );

      Customer customer = new Customer(
          "cus_1",
          "Cliente",
          "12829786670",
          "cliente@exemplo.com",
          "+5511999999999",
          null,
          null,
          Instant.now(),
          Instant.now()
      );

      provider.generatePix(tenantId, charge, customer, 3600);

      assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
      JsonNode payload = mapper.readTree(capturedBody.get());
      JsonNode urls = payload.path("notification_urls");
      assertThat(urls.isArray()).isTrue();
      assertThat(urls.get(0).asText()).isEqualTo("https://apipratudo.com/v1/pix/webhook");
    } finally {
      server.stop(0);
    }
  }

  private static void handleOrders(
      HttpExchange exchange,
      AtomicReference<String> capturedBody,
      CountDownLatch latch,
      int port
  ) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    capturedBody.set(body);
    latch.countDown();
    String response = ("{\n"
        + "  \"id\": \"ORDE_TEST\",\n"
        + "  \"qr_codes\": [\n"
        + "    {\n"
        + "      \"id\": \"qr1\",\n"
        + "      \"text\": \"pixcopy\",\n"
        + "      \"expiration_date\": \"2026-01-26T12:00:00-03:00\",\n"
        + "      \"links\": [\n"
        + "        {\"rel\": \"QRCODE.BASE64\", \"href\": \"http://localhost:" + port + "/qrcode\"}\n"
        + "      ]\n"
        + "    }\n"
        + "  ]\n"
        + "}\n");
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private static void handleQrCode(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    byte[] bytes = "BASE64DATA".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/plain");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private static String base64Key() {
    return Base64.getEncoder().encodeToString(new byte[32]);
  }
}
