package com.apipratudo.portal.error;

public class QuotaServiceException extends RuntimeException {

  private final int statusCode;
  private final String error;

  public QuotaServiceException(int statusCode, String error, String message) {
    super(message);
    this.statusCode = statusCode;
    this.error = error;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getError() {
    return error;
  }
}
