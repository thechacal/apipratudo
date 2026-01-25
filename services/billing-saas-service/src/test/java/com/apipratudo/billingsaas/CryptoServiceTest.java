package com.apipratudo.billingsaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apipratudo.billingsaas.config.CryptoProperties;
import com.apipratudo.billingsaas.crypto.CryptoService;
import com.apipratudo.billingsaas.model.EncryptedValue;
import org.junit.jupiter.api.Test;

class CryptoServiceTest {

  @Test
  void encryptDecryptWithAad() {
    CryptoProperties props = new CryptoProperties();
    props.setMasterKeyBase64("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    CryptoService service = new CryptoService(props);

    EncryptedValue encrypted = service.encrypt("segredo", "tenant:PAGBANK");
    String plain = service.decrypt(encrypted, "tenant:PAGBANK");

    assertThat(plain).isEqualTo("segredo");
  }

  @Test
  void decryptWithWrongAadFails() {
    CryptoProperties props = new CryptoProperties();
    props.setMasterKeyBase64("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    CryptoService service = new CryptoService(props);

    EncryptedValue encrypted = service.encrypt("segredo", "tenant:PAGBANK");

    assertThatThrownBy(() -> service.decrypt(encrypted, "other:PAGBANK"))
        .isInstanceOf(IllegalStateException.class);
  }
}
