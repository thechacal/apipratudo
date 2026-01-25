package com.apipratudo.billingsaas.model;

import java.util.Locale;

public enum PagbankEnvironment {
  SANDBOX,
  PRODUCTION;

  public static PagbankEnvironment fromString(String value) {
    if (value == null) {
      return SANDBOX;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "PRODUCTION", "PROD", "LIVE" -> PRODUCTION;
      default -> SANDBOX;
    };
  }
}
