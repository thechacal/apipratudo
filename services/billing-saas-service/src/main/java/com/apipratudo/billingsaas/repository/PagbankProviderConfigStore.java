package com.apipratudo.billingsaas.repository;

import com.apipratudo.billingsaas.model.PagbankProviderConfig;
import java.util.Optional;

public interface PagbankProviderConfigStore {

  PagbankProviderConfig save(String tenantId, PagbankProviderConfig config);

  Optional<PagbankProviderConfig> find(String tenantId);

  void delete(String tenantId);
}
