package com.apipratudo.maismilionaria.error;

public class UpstreamTimeoutException extends RuntimeException {

  public UpstreamTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
