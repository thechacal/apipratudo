package com.apipratudo.duplasena.error;

public class UpstreamTimeoutException extends RuntimeException {

  public UpstreamTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
