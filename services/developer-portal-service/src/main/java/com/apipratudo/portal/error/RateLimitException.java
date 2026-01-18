package com.apipratudo.portal.error;

public class RateLimitException extends RuntimeException {

  private final String error;

  public RateLimitException(String error, String message) {
    super(message);
    this.error = error;
  }

  public String getError() {
    return error;
  }
}
