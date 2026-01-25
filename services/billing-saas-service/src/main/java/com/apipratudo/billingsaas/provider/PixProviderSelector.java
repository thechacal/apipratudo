package com.apipratudo.billingsaas.provider;

import com.apipratudo.billingsaas.repository.PagbankProviderConfigStore;
import org.springframework.stereotype.Component;

@Component
public class PixProviderSelector {

  private final PagbankProviderConfigStore pagbankProviderConfigStore;
  private final PagbankPixProvider pagbankPixProvider;
  private final FakePixProvider fakePixProvider;

  public PixProviderSelector(
      PagbankProviderConfigStore pagbankProviderConfigStore,
      PagbankPixProvider pagbankPixProvider,
      FakePixProvider fakePixProvider
  ) {
    this.pagbankProviderConfigStore = pagbankProviderConfigStore;
    this.pagbankPixProvider = pagbankPixProvider;
    this.fakePixProvider = fakePixProvider;
  }

  public PixProvider select(String tenantId) {
    return pagbankProviderConfigStore.find(tenantId)
        .filter(config -> config.enabled())
        .map(config -> (PixProvider) pagbankPixProvider)
        .orElse(fakePixProvider);
  }
}
