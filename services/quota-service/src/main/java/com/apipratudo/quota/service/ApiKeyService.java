package com.apipratudo.quota.service;

import com.apipratudo.quota.config.KeyCreationProperties;
import com.apipratudo.quota.config.PlanProperties;
import com.apipratudo.quota.dto.ActivatePremiumRequest;
import com.apipratudo.quota.dto.ActivatePremiumResponse;
import com.apipratudo.quota.dto.ApiKeyCreateRequest;
import com.apipratudo.quota.dto.ApiKeyCreateResponse;
import com.apipratudo.quota.dto.ApiKeyLimits;
import com.apipratudo.quota.dto.ApiKeyResponse;
import com.apipratudo.quota.dto.ApiKeyRotateResponse;
import com.apipratudo.quota.dto.CreateFreeKeyRequest;
import com.apipratudo.quota.dto.CreateFreeKeyResponse;
import com.apipratudo.quota.error.ResourceNotFoundException;
import com.apipratudo.quota.error.KeyCreationLimitException;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.ApiKeyStatus;
import com.apipratudo.quota.model.Plan;
import com.apipratudo.quota.repository.ApiKeyRepository;
import com.apipratudo.quota.repository.KeyCreationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApiKeyService {

  private final ApiKeyRepository repository;
  private final Clock clock;
  private final PlanProperties planProperties;
  private final KeyCreationRepository keyCreationRepository;
  private final KeyCreationProperties keyCreationProperties;

  public ApiKeyService(
      ApiKeyRepository repository,
      Clock clock,
      PlanProperties planProperties,
      KeyCreationRepository keyCreationRepository,
      KeyCreationProperties keyCreationProperties
  ) {
    this.repository = repository;
    this.clock = clock;
    this.planProperties = planProperties;
    this.keyCreationRepository = keyCreationRepository;
    this.keyCreationProperties = keyCreationProperties;
  }

  public ApiKeyCreateResponse create(ApiKeyCreateRequest request) {
    String id = UUID.randomUUID().toString();
    String apiKey = UUID.randomUUID().toString().replace("-", "");
    Instant now = Instant.now(clock);

    Plan plan = request.plan() == null ? Plan.FREE : request.plan();
    ApiKeyLimits limits = request.limits();

    ApiKey model = new ApiKey(
        id,
        HashingUtils.sha256Hex(apiKey),
        request.name(),
        request.owner(),
        request.ownerEmail(),
        StringUtils.hasText(request.orgName()) ? request.orgName() : request.owner(),
        limits,
        now,
        ApiKeyStatus.ACTIVE,
        plan,
        null,
        0,
        null,
        0
    );
    repository.save(model);

    return new ApiKeyCreateResponse(id, apiKey, request.name(), request.owner(), request.ownerEmail(),
        StringUtils.hasText(request.orgName()) ? request.orgName() : request.owner(), plan, limits, now);
  }

  public CreateFreeKeyResponse createFreeKey(CreateFreeKeyRequest request) {
    String email = normalizeEmail(request.email());
    String org = normalizeOrg(request.org());
    LocalDate day = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC);

    KeyCreationRepository.KeyCreationResult result = keyCreationRepository.tryReserve(
        email,
        org,
        day,
        keyCreationProperties.getMaxPerEmailPerDay(),
        keyCreationProperties.getMaxPerOrgPerDay()
    );

    if (!result.allowed()) {
      throw new KeyCreationLimitException("Free key creation limit reached");
    }

    String id = UUID.randomUUID().toString();
    String apiKey = UUID.randomUUID().toString().replace("-", "");
    Instant now = Instant.now(clock);

    Plan plan = Plan.FREE;
    ApiKeyLimits limits = planProperties.limitsFor(plan);

    ApiKey model = new ApiKey(
        id,
        HashingUtils.sha256Hex(apiKey),
        org,
        org,
        email,
        org,
        limits,
        now,
        ApiKeyStatus.ACTIVE,
        plan,
        null,
        0,
        null,
        0
    );
    repository.save(model);

    return new CreateFreeKeyResponse(id, apiKey, email, org, plan, limits, now);
  }

  public ApiKeyResponse get(String id) {
    ApiKey apiKey = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("API key not found"));
    return new ApiKeyResponse(apiKey.id(), apiKey.name(), apiKey.owner(), apiKey.ownerEmail(), apiKey.orgName(),
        apiKey.plan(), apiKey.limits(), apiKey.createdAt(), apiKey.status());
  }

  public ApiKeyRotateResponse rotate(String id) {
    ApiKey apiKey = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("API key not found"));
    String newKey = UUID.randomUUID().toString().replace("-", "");
    ApiKey updated = new ApiKey(
        apiKey.id(),
        HashingUtils.sha256Hex(newKey),
        apiKey.name(),
        apiKey.owner(),
        apiKey.ownerEmail(),
        apiKey.orgName(),
        apiKey.limits(),
        apiKey.createdAt(),
        apiKey.status(),
        apiKey.plan(),
        apiKey.minuteBucket(),
        apiKey.minuteCount(),
        apiKey.dayBucket(),
        apiKey.dayCount()
    );
    repository.save(updated);
    return new ApiKeyRotateResponse(apiKey.id(), newKey);
  }

  public ApiKeyResponse updateStatus(String id, ApiKeyStatus status) {
    ApiKey apiKey = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("API key not found"));
    ApiKey updated = new ApiKey(
        apiKey.id(),
        apiKey.apiKeyHash(),
        apiKey.name(),
        apiKey.owner(),
        apiKey.ownerEmail(),
        apiKey.orgName(),
        apiKey.limits(),
        apiKey.createdAt(),
        status,
        apiKey.plan(),
        apiKey.minuteBucket(),
        apiKey.minuteCount(),
        apiKey.dayBucket(),
        apiKey.dayCount()
    );
    repository.save(updated);
    return new ApiKeyResponse(updated.id(), updated.name(), updated.owner(), updated.ownerEmail(), updated.orgName(),
        updated.plan(), updated.limits(), updated.createdAt(), updated.status());
  }

  public ActivatePremiumResponse activatePremium(ActivatePremiumRequest request) {
    String apiKeyHash = resolveHash(request.apiKey(), request.apiKeyHash());
    ApiKey apiKey = repository.findByApiKeyHash(apiKeyHash)
        .orElseThrow(() -> new ResourceNotFoundException("API key not found"));

    Plan plan = request.plan() == null ? Plan.PREMIUM : request.plan();
    ApiKeyLimits limits = request.limits() == null ? planProperties.limitsFor(plan) : request.limits();

    ApiKey updated = new ApiKey(
        apiKey.id(),
        apiKey.apiKeyHash(),
        apiKey.name(),
        apiKey.owner(),
        apiKey.ownerEmail(),
        apiKey.orgName(),
        limits,
        apiKey.createdAt(),
        apiKey.status(),
        plan,
        apiKey.minuteBucket(),
        apiKey.minuteCount(),
        apiKey.dayBucket(),
        apiKey.dayCount()
    );
    repository.save(updated);
    return new ActivatePremiumResponse(updated.id(), updated.plan(), updated.limits());
  }

  private String resolveHash(String apiKey, String apiKeyHash) {
    if (StringUtils.hasText(apiKey)) {
      return HashingUtils.sha256Hex(apiKey.trim());
    }
    if (StringUtils.hasText(apiKeyHash)) {
      return apiKeyHash.trim();
    }
    throw new IllegalArgumentException("apiKey or apiKeyHash is required");
  }

  private String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase();
  }

  private String normalizeOrg(String org) {
    return org == null ? null : org.trim();
  }
}
