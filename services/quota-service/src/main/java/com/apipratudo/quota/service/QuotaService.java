package com.apipratudo.quota.service;

import com.apipratudo.quota.dto.QuotaConsumeRequest;
import com.apipratudo.quota.dto.QuotaConsumeResponse;
import com.apipratudo.quota.dto.QuotaReason;
import com.apipratudo.quota.dto.QuotaRefundRequest;
import com.apipratudo.quota.dto.QuotaRefundResponse;
import com.apipratudo.quota.dto.QuotaStatusResponse;
import com.apipratudo.quota.error.ResourceNotFoundException;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.ApiKeyStatus;
import com.apipratudo.quota.model.QuotaDecision;
import com.apipratudo.quota.model.QuotaRefundDecision;
import com.apipratudo.quota.model.QuotaStatus;
import com.apipratudo.quota.repository.ApiKeyRepository;
import com.apipratudo.quota.repository.QuotaStore;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class QuotaService {

  private final ApiKeyRepository apiKeyRepository;
  private final QuotaStore quotaStore;

  public QuotaService(ApiKeyRepository apiKeyRepository, QuotaStore quotaStore) {
    this.apiKeyRepository = apiKeyRepository;
    this.quotaStore = quotaStore;
  }

  public QuotaConsumeResult consume(QuotaConsumeRequest request) {
    int cost = request.cost() == null ? 1 : request.cost();
    Optional<ApiKey> apiKey = findActiveApiKey(request.apiKey());
    if (apiKey.isEmpty()) {
      QuotaConsumeResponse response = new QuotaConsumeResponse(false, QuotaReason.INVALID_KEY, null, null, null);
      return new QuotaConsumeResult(HttpStatus.UNAUTHORIZED, response);
    }

    QuotaDecision decision = quotaStore.consume(apiKey.get(), request.requestId(), request.route(), cost);
    HttpStatus status = decision.allowed() ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;
    QuotaConsumeResponse response = new QuotaConsumeResponse(
        decision.allowed(),
        decision.reason(),
        decision.limit(),
        decision.remaining(),
        decision.resetAt()
    );
    return new QuotaConsumeResult(status, response);
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

    QuotaStatus status = quotaStore.status(apiKey.get());
    return new QuotaStatusResponse(
        apiKey.get().id(),
        new QuotaStatusResponse.QuotaWindowStatus(
            status.minute().limit(),
            status.minute().used(),
            status.minute().remaining(),
            status.minute().resetAt()
        ),
        new QuotaStatusResponse.QuotaWindowStatus(
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
