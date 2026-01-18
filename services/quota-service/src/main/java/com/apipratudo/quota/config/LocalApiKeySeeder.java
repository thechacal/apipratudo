package com.apipratudo.quota.config;

import com.apipratudo.quota.dto.ApiKeyLimits;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.ApiKeyCredits;
import com.apipratudo.quota.model.ApiKeyStatus;
import com.apipratudo.quota.model.Plan;
import com.apipratudo.quota.repository.ApiKeyRepository;
import com.apipratudo.quota.service.HashingUtils;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalApiKeySeeder {

  private static final Logger log = LoggerFactory.getLogger(LocalApiKeySeeder.class);

  private final ApiKeyRepository repository;
  private final Clock clock;

  public LocalApiKeySeeder(ApiKeyRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seed() {
    List<SeedKey> seeds = List.of(
        new SeedKey("wh-test", "Webhook Local", "webhook-service"),
        new SeedKey("dev-test", "Dev Local", "local-dev")
    );

    ApiKeyLimits limits = new ApiKeyLimits(60, 1000);
    Instant now = Instant.now(clock);

    for (SeedKey seed : seeds) {
      String hash = HashingUtils.sha256Hex(seed.rawKey());
      if (repository.findByApiKeyHash(hash).isPresent()) {
        continue;
      }
      ApiKey model = new ApiKey(
          UUID.randomUUID().toString(),
          hash,
          seed.name(),
          seed.owner(),
          null,
          seed.owner(),
          limits,
          now,
          ApiKeyStatus.ACTIVE,
          Plan.FREE,
          new ApiKeyCredits(0),
          null,
          0,
          null,
          0
      );
      repository.save(model);
      log.info("Seeded local api key name={} owner={}", seed.name(), seed.owner());
    }
  }

  private record SeedKey(String rawKey, String name, String owner) {
  }
}
