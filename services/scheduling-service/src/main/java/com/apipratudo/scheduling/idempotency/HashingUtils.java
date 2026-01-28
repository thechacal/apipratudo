package com.apipratudo.scheduling.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashingUtils {

  private HashingUtils() {
  }

  public static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (byte b : hash) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
