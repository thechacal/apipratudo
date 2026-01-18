package com.apipratudo.quota.service;

import com.apipratudo.quota.dto.QuotaConsumeRequest;
import com.apipratudo.quota.dto.QuotaConsumeResponse;
import com.apipratudo.quota.dto.QuotaReason;
import com.apipratudo.quota.dto.QuotaRefundRequest;
import com.apipratudo.quota.dto.QuotaRefundResponse;
import com.apipratudo.quota.dto.QuotaStatusResponse;
import com.apipratudo.quota.dto.QuotaUsage;
import com.apipratudo.quota.dto.ApiKeyLimits;
import com.apipratudo.quota.error.ResourceNotFoundException;
import com.apipratudo.quota.config.PlanProperties;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.ApiKeyStatus;
import com.apipratudo.quota.model.Plan;
import com.apipratudo.quota.model.QuotaDecision;
import com.apipratudo.quota.model.QuotaRefundDecision;
import com.apipratudo.quota.model.QuotaStatus;
import com.apipratudo.quota.model.QuotaWindows;
import com.apipratudo.quota.repository.ApiKeyRepository;
import com.apipratudo.quota.repository.QuotaStore;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class QuotaService {

  private final ApiKeyRepository apiKeyRepository;
  private final QuotaStore quotaStore;
  private final QuotaWindowCalculator windowCalculator;
  private final PlanProperties planProperties;

  public QuotaService(
      ApiKeyRepository apiKeyRepository,
      QuotaStore quotaStore,
      QuotaWindowCalculator windowCalculator,
      PlanProperties planProperties
  ) {
    this.apiKeyRepository = apiKeyRepository;
    this.quotaStore = quotaStore;
    this.windowCalculator = windowCalculator;
    this.planProperties = planProperties;
  }

  public QuotaConsumeResult consume(QuotaConsumeRequest request) {
    int cost = request.cost() == null ? 1 : request.cost();
    Optional<ApiKey> apiKey = findActiveApiKey(request.apiKey());
    if (apiKey.isEmpty()) {
      QuotaConsumeResponse response = new QuotaConsumeResponse(false, QuotaReason.INVALID_KEY, null, null, null,
          null, null, null, null, null, null, null);
      return new QuotaConsumeResult(HttpStatus.UNAUTHORIZED, response);
    }

    ApiKey model = withDerivedPlan(apiKey.get());
    QuotaWindows windows = windowCalculator.currentWindows();
    QuotaDecision decision = quotaStore.consume(model, request.requestId(), request.route(), cost, windows);

    if (decision.allowed()) {
      QuotaConsumeResponse response = new QuotaConsumeResponse(
          true,
          null,
          decision.limit(),
          decision.remaining(),
          decision.resetAt(),
          null,
          null,
          model.plan(),
          model.limits(),
          null,
          null,
          model.credits()
      );
      return new QuotaConsumeResult(HttpStatus.OK, response);
    }

    if (decision.reason() == QuotaReason.QUOTA_EXCEEDED) {
      QuotaStatus status = quotaStore.status(model, windows);
      QuotaUsage usage = toUsage(status);
      QuotaConsumeResponse response = new QuotaConsumeResponse(
          false,
          decision.reason(),
          decision.limit(),
          decision.remaining(),
          decision.resetAt(),
          "QUOTA_EXCEEDED",
          "Cota esgotada. Recarregue para continuar.",
          model.plan(),
          model.limits(),
          usage,
          new QuotaConsumeResponse.UpgradeHint("/v1/keys/upgrade", "POST"),
          model.credits()
      );
      return new QuotaConsumeResult(HttpStatus.PAYMENT_REQUIRED, response);
    }

    QuotaConsumeResponse response = new QuotaConsumeResponse(
        false,
        decision.reason(),
        decision.limit(),
        decision.remaining(),
        decision.resetAt(),
        null,
        null,
        model.plan(),
        model.limits(),
        null,
        null,
        model.credits()
    );
    return new QuotaConsumeResult(HttpStatus.TOO_MANY_REQUESTS, response);
  }

  public QuotaRefundResult refund(QuotaRefundRequest request) {
    Optional<ApiKey> apiKey = findActiveApiKey(request.apiKey());
    if (apiKey.isEmpty()) {
      QuotaRefundResponse response = new QuotaRefundResponse(false, QuotaReason.INVALID_KEY, null, null, null);
      return new QuotaRefundResult(HttpStatus.UNAUTHORIZED, response);
    }

    QuotaRefundDecision decision = quotaStore.refund(apiKey.get(), request.requestId());
    QuotaRefundResponse response = new QuotaRefundResponse(
        decision.refunded(),
        decision.reason(),
        decision.limit(),
        decision.remaining(),
        decision.resetAt()
    );
    return new QuotaRefundResult(HttpStatus.OK, response);
  }

  public QuotaStatusResponse status(String apiKeyValue) {
    Optional<ApiKey> apiKey = findApiKey(apiKeyValue);
    if (apiKey.isEmpty()) {
      throw new ResourceNotFoundException("API key not found");
    }

    ApiKey model = withDerivedPlan(apiKey.get());
    QuotaWindows windows = windowCalculator.currentWindows();
    QuotaStatus status = quotaStore.status(model, windows);
    return new QuotaStatusResponse(
        model.id(),
        model.plan(),
        model.limits(),
        toUsage(status),
        model.credits()
    );
  }

  private QuotaUsage toUsage(QuotaStatus status) {
    return new QuotaUsage(
        new QuotaUsage.WindowStatus(
            status.minute().limit(),
            status.minute().used(),
            status.minute().remaining(),
            status.minute().resetAt()
        ),
        new QuotaUsage.WindowStatus(
            status.day().limit(),
            status.day().used(),
            status.day().remaining(),
            status.day().resetAt()
        )
    );
  }

  private Optional<ApiKey> findActiveApiKey(String apiKeyValue) {
    String apiKeyHash = HashingUtils.sha256Hex(apiKeyValue);
    return apiKeyRepository.findByApiKeyHash(apiKeyHash)
        .filter(apiKey -> apiKey.status() == ApiKeyStatus.ACTIVE);
  }

  private Optional<ApiKey> findApiKey(String apiKeyValue) {
    String apiKeyHash = HashingUtils.sha256Hex(apiKeyValue);
    return apiKeyRepository.findByApiKeyHash(apiKeyHash);
  }

  private ApiKey withDerivedPlan(ApiKey apiKey) {
    long credits = apiKey.credits() == null ? 0 : apiKey.credits().remaining();
    Plan plan = credits > 0 ? Plan.PREMIUM : Plan.FREE;
    ApiKeyLimits limits = effectiveLimits(apiKey, plan);
    return new ApiKey(
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
        apiKey.credits(),
        apiKey.minuteBucket(),
        apiKey.minuteCount(),
        apiKey.dayBucket(),
        apiKey.dayCount()
    );
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

  public record QuotaConsumeResult(HttpStatus status, QuotaConsumeResponse response) {
  }

  public record QuotaRefundResult(HttpStatus status, QuotaRefundResponse response) {
  }
}
