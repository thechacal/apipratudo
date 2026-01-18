package com.apipratudo.quota.error;

public class KeyCreationLimitException extends RuntimeException {

  public KeyCreationLimitException(String message) {
    super(message);
  }
}
