package com.apipratudo.billingsaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apipratudo.billingsaas.config.CryptoProperties;
import com.apipratudo.billingsaas.crypto.CryptoService;
import com.apipratudo.billingsaas.error.ConfigurationException;
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

  @Test
  void invalidBase64KeyFailsWithClearMessage() {
    CryptoProperties props = new CryptoProperties();
    props.setMasterKeyBase64("not-base64");
    CryptoService service = new CryptoService(props);

    assertThatThrownBy(() -> service.encrypt("segredo", "tenant:PAGBANK"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("base64");
  }

  @Test
  void invalidLengthKeyFailsWithClearMessage() {
    CryptoProperties props = new CryptoProperties();
    props.setMasterKeyBase64("MDEyMzQ1Njc4OWFiY2RlZg==");
    CryptoService service = new CryptoService(props);

    assertThatThrownBy(() -> service.encrypt("segredo", "tenant:PAGBANK"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("32 bytes");
  }
}
