package com.apipratudo.billingsaas.crypto;

import com.apipratudo.billingsaas.config.CryptoProperties;
import com.apipratudo.billingsaas.error.ConfigurationException;
import com.apipratudo.billingsaas.model.EncryptedValue;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CryptoService {

  private static final int VERSION = 1;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final String masterKeyBase64;
  private volatile SecretKey masterKey;
  private final SecureRandom random = new SecureRandom();

  public CryptoService(CryptoProperties properties) {
    this.masterKeyBase64 = properties.getMasterKeyBase64();
  }

  public EncryptedValue encrypt(String plaintext, String aad) {
    if (!StringUtils.hasText(plaintext)) {
      throw new IllegalArgumentException("Empty payload cannot be encrypted");
    }
    byte[] iv = new byte[IV_BYTES];
    random.nextBytes(iv);
    byte[] cipherText = doCipher(Cipher.ENCRYPT_MODE, plaintext.getBytes(StandardCharsets.UTF_8), iv, aad);
    return new EncryptedValue(
        Base64.getEncoder().encodeToString(cipherText),
        Base64.getEncoder().encodeToString(iv),
        VERSION
    );
  }

  public String decrypt(EncryptedValue value, String aad) {
    if (value == null || !StringUtils.hasText(value.cipherTextBase64())) {
      return null;
    }
    byte[] iv = Base64.getDecoder().decode(value.ivBase64());
    byte[] cipherText = Base64.getDecoder().decode(value.cipherTextBase64());
    byte[] plain = doCipher(Cipher.DECRYPT_MODE, cipherText, iv, aad);
    return new String(plain, StandardCharsets.UTF_8);
  }

  private byte[] doCipher(int mode, byte[] input, byte[] iv, String aad) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(mode, resolveKey(), new GCMParameterSpec(TAG_BITS, iv));
      if (StringUtils.hasText(aad)) {
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
      }
      return cipher.doFinal(input);
    } catch (ConfigurationException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Encryption failure", ex);
    }
  }

  private SecretKey resolveKey() {
    if (masterKey != null) {
      return masterKey;
    }
    synchronized (this) {
      if (masterKey != null) {
        return masterKey;
      }
      masterKey = loadKey(masterKeyBase64);
      return masterKey;
    }
  }

  private SecretKey loadKey(String base64) {
    if (!StringUtils.hasText(base64)) {
      throw new ConfigurationException("BILLING_SAAS_MASTER_KEY_BASE64 ausente ou vazia");
    }
    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException ex) {
      throw new ConfigurationException("BILLING_SAAS_MASTER_KEY_BASE64 invalida (base64)", ex);
    }
    if (keyBytes.length != 32) {
      throw new ConfigurationException("BILLING_SAAS_MASTER_KEY_BASE64 invalida (esperado 32 bytes)");
    }
    return new SecretKeySpec(keyBytes, "AES");
  }
}
