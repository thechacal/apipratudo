package com.apipratudo.portal.dto;

import java.util.Locale;

public enum CreditPackage {
  START,
  PRO,
  SCALE;

  public static CreditPackage from(String value) {
    if (value == null || value.isBlank()) {
      return START;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if ("PREMIUM".equals(normalized)) {
      return START;
    }
    return CreditPackage.valueOf(normalized);
  }
}
