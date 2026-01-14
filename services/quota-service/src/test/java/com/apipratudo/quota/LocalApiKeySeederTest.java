package com.apipratudo.quota;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.quota.dto.QuotaConsumeRequest;
import com.apipratudo.quota.service.QuotaService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class LocalApiKeySeederTest {

  @Autowired
  private QuotaService quotaService;

  @Test
  void localSeedAcceptsWhTest() {
    QuotaConsumeRequest request = new QuotaConsumeRequest(
        "wh-test",
        "req-" + UUID.randomUUID(),
        "GET /v1/webhooks",
        1
    );

    QuotaService.QuotaConsumeResult result = quotaService.consume(request);
    assertThat(result.status().value()).isEqualTo(200);
    assertThat(result.response().allowed()).isTrue();
  }

  @Test
  void localSeedAcceptsDevTest() {
    QuotaConsumeRequest request = new QuotaConsumeRequest(
        "dev-test",
        "req-" + UUID.randomUUID(),
        "GET /v1/webhooks",
        1
    );

    QuotaService.QuotaConsumeResult result = quotaService.consume(request);
    assertThat(result.status().value()).isEqualTo(200);
    assertThat(result.response().allowed()).isTrue();
  }
}
