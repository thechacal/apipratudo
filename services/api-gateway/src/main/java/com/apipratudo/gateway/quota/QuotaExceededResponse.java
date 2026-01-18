package com.apipratudo.gateway.quota;

public record QuotaExceededResponse(
    String error,
    String message,
    Upgrade upgrade
) {
  public record Upgrade(String endpoint, String docs) {
  }
}
