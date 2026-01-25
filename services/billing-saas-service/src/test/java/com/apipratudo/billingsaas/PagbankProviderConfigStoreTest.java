package com.apipratudo.billingsaas;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.billingsaas.model.EncryptedValue;
import com.apipratudo.billingsaas.model.PagbankEnvironment;
import com.apipratudo.billingsaas.model.PagbankProviderConfig;
import com.apipratudo.billingsaas.repository.InMemoryPagbankProviderConfigStore;
import com.apipratudo.billingsaas.repository.PagbankProviderConfigStore;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PagbankProviderConfigStoreTest {

  @Test
  void saveFindDeleteConfig() {
    PagbankProviderConfigStore store = new InMemoryPagbankProviderConfigStore();
    String tenantId = "tenant-test";
    PagbankProviderConfig config = new PagbankProviderConfig(
        tenantId,
        true,
        PagbankEnvironment.SANDBOX,
        new EncryptedValue("cipher", "iv", 1),
        null,
        "deadbeef",
        Instant.EPOCH,
        Instant.EPOCH,
        null
    );

    store.save(tenantId, config);
    PagbankProviderConfig loaded = store.find(tenantId).orElseThrow();
    assertThat(loaded.tenantId()).isEqualTo(tenantId);
    assertThat(loaded.environment()).isEqualTo(PagbankEnvironment.SANDBOX);

    store.delete(tenantId);
    assertThat(store.find(tenantId)).isEmpty();
  }
}
