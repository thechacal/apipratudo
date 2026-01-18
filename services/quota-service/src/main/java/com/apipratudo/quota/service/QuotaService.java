package com.apipratudo.quota.service;

import com.apipratudo.quota.dto.QuotaConsumeRequest;
import com.apipratudo.quota.dto.QuotaConsumeResponse;
import com.apipratudo.quota.dto.QuotaReason;
import com.apipratudo.quota.dto.QuotaRefundRequest;
import com.apipratudo.quota.dto.QuotaRefundResponse;
import com.apipratudo.quota.dto.QuotaStatusResponse;
import com.apipratudo.quota.dto.QuotaUsage;
import com.apipratudo.quota.error.ResourceNotFoundException;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.ApiKeyStatus;
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

  public QuotaService(
      ApiKeyRepository apiKeyRepository,
      QuotaStore quotaStore,
      QuotaWindowCalculator windowCalculator
  ) {
    this.apiKeyRepository = apiKeyRepository;
    this.quotaStore = quotaStore;
    this.windowCalculator = windowCalculator;
  }

  public QuotaConsumeResult consume(QuotaConsumeRequest request) {
    int cost = request.cost() == null ? 1 : request.cost();
    Optional<ApiKey> apiKey = findActiveApiKey(request.apiKey());
    if (apiKey.isEmpty()) {
      QuotaConsumeResponse response = new QuotaConsumeResponse(false, QuotaReason.INVALID_KEY, null, null, null,
          null, null, null, null, null, null);
      return new QuotaConsumeResult(HttpStatus.UNAUTHORIZED, response);
    }

    ApiKey model = apiKey.get();
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
          null
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
          "Você consumiu todo o seu pacote. Assine o Premium.",
          model.plan(),
          model.limits(),
          usage,
          new QuotaConsumeResponse.UpgradeHint("/v1/keys/upgrade", "POST")
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
        null
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

    QuotaWindows windows = windowCalculator.currentWindows();
    QuotaStatus status = quotaStore.status(apiKey.get(), windows);
    return new QuotaStatusResponse(
        apiKey.get().id(),
        apiKey.get().plan(),
        apiKey.get().limits(),
        toUsage(status)
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

  public record QuotaConsumeResult(HttpStatus status, QuotaConsumeResponse response) {
  }

  public record QuotaRefundResult(HttpStatus status, QuotaRefundResponse response) {
  }
}
