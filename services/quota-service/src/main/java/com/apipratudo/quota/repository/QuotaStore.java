package com.apipratudo.quota.repository;

import com.apipratudo.quota.model.ApiKey;
import com.apipratudo.quota.model.QuotaDecision;

public interface QuotaStore {

  QuotaDecision consume(ApiKey apiKey, String requestId, String route, int cost);
}
