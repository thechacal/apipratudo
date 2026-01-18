package com.apipratudo.quota.service;

import com.apipratudo.quota.config.KeyCreationProperties;
import com.apipratudo.quota.config.PlanProperties;
import com.apipratudo.quota.dto.AddCreditsRequest;
import com.apipratudo.quota.dto.AddCreditsResponse;
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
import com.apipratudo.quota.model.ApiKeyCredits;
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
        new ApiKeyCredits(0),
        null,
        0,
        null,
        0
    );
    repository.save(model);

    return new ApiKeyCreateResponse(id, apiKey, request.name(), request.owner(), request.ownerEmail(),
        StringUtils.hasText(request.orgName()) ? request.orgName() : request.owner(), plan, limits, now,
        model.credits());
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
        new ApiKeyCredits(0),
        null,
        0,
        null,
        0
    );
    repository.save(model);

    return new CreateFreeKeyResponse(id, apiKey, email, org, plan, limits, now, model.credits());
  }

  public ApiKeyResponse get(String id) {
    ApiKey apiKey = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("API key not found"));
    Plan plan = derivePlan(apiKey);
    ApiKeyLimits limits = effectiveLimits(apiKey, plan);
    return new ApiKeyResponse(apiKey.id(), apiKey.name(), apiKey.owner(), apiKey.ownerEmail(), apiKey.orgName(),
        plan, limits, apiKey.createdAt(), apiKey.status(), apiKey.credits());
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
        apiKey.credits(),
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
        apiKey.credits(),
        apiKey.minuteBucket(),
        apiKey.minuteCount(),
        apiKey.dayBucket(),
        apiKey.dayCount()
    );
    repository.save(updated);
    Plan plan = derivePlan(updated);
    ApiKeyLimits limits = effectiveLimits(updated, plan);
    return new ApiKeyResponse(updated.id(), updated.name(), updated.owner(), updated.ownerEmail(), updated.orgName(),
        plan, limits, updated.createdAt(), updated.status(), updated.credits());
  }

  public AddCreditsResponse addCredits(AddCreditsRequest request) {
    String apiKeyHash = resolveHash(request.apiKey(), request.apiKeyHash());
    ApiKey apiKey = repository.findByApiKeyHash(apiKeyHash)
        .orElseThrow(() -> new ResourceNotFoundException("API key not found"));

    long toAdd = request.credits();
    long current = apiKey.credits() == null ? 0 : apiKey.credits().remaining();
    long updatedRemaining = current + Math.max(toAdd, 0);
    ApiKey updated = new ApiKey(
        apiKey.id(),
        apiKey.apiKeyHash(),
        apiKey.name(),
        apiKey.owner(),
        apiKey.ownerEmail(),
        apiKey.orgName(),
        apiKey.limits(),
        apiKey.createdAt(),
        apiKey.status(),
        apiKey.plan(),
        new ApiKeyCredits(updatedRemaining),
        null,
        0,
        null,
        0
    );
    repository.save(updated);
    Plan plan = derivePlan(updated);
    return new AddCreditsResponse(updated.id(), toAdd, updatedRemaining, plan);
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

  private Plan derivePlan(ApiKey apiKey) {
    long remaining = apiKey.credits() == null ? 0 : apiKey.credits().remaining();
    return remaining > 0 ? Plan.PREMIUM : Plan.FREE;
  }

  private ApiKeyLimits effectiveLimits(ApiKey apiKey, Plan plan) {
    if (plan == Plan.PREMIUM) {
      return planProperties.limitsFor(Plan.PREMIUM);
    }
    ApiKeyLimits stored = apiKey.limits();
    if (stored == null || stored.requestsPerMinute() <= 0 || stored.requestsPerDay() <= 0) {
      return planProperties.limitsFor(Plan.FREE);
    }
    return stored;
  }
}
