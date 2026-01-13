package com.apipratudo.quota.repository;

import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.QuotaDecision;
import com.apipratudo.quota.model.QuotaRefundDecision;
import com.apipratudo.quota.model.QuotaStatus;
import com.apipratudo.quota.model.QuotaWindows;

public interface QuotaStore {

  QuotaDecision consume(ApiKey apiKey, String requestId, String route, int cost, QuotaWindows windows);

  QuotaRefundDecision refund(ApiKey apiKey, String requestId);

  QuotaStatus status(ApiKey apiKey, QuotaWindows windows);
}
