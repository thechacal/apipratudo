package com.apipratudo.quota.service;

import com.apipratudo.quota.dto.QuotaConsumeRequest;
import com.apipratudo.quota.dto.QuotaConsumeResponse;
import com.apipratudo.quota.dto.QuotaReason;
import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.QuotaDecision;
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
    String apiKeyHash = HashingUtils.sha256Hex(request.apiKey());
    Optional<ApiKey> apiKey = apiKeyRepository.findByApiKeyHash(apiKeyHash);

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

  public record QuotaConsumeResult(HttpStatus status, QuotaConsumeResponse response) {
  }
}
