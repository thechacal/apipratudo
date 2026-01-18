package com.apipratudo.gateway.quota;

public record QuotaExceededResponse(
    String error,
    String message,
    HowToUpgrade howToUpgrade
) {
  public record HowToUpgrade(String requestPix, String docs) {
  }
}
