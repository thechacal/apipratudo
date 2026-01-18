package com.apipratudo.portal.rate;

import com.apipratudo.portal.config.RateLimitProperties;
import com.apipratudo.portal.error.RateLimitException;
import com.apipratudo.portal.repository.KeyRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KeyRequestLimiter {

  private final KeyRequestRepository repository;
  private final RateLimitProperties properties;
  private final Clock clock;

  public KeyRequestLimiter(KeyRequestRepository repository, RateLimitProperties properties, Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  public void assertAllowed(String email, String org) {
    String normalizedEmail = normalize(email);
    String normalizedOrg = normalize(org);

    LocalDate day = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC);
    KeyRequestRepository.KeyRequestResult result = repository.tryReserve(
        normalizedEmail,
        normalizedOrg,
        day,
        properties.getEmail().getMaxPerDay(),
        properties.getOrg().getMaxPerDay()
    );

    if (!result.allowed()) {
      throw new RateLimitException("KEY_CREATION_LIMIT", "Free key request limit reached");
    }
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toLowerCase();
  }
}
