package com.apipratudo.billing.error;

public class PagBankException extends RuntimeException {

  private final int statusCode;
  private final String body;

  public PagBankException(int statusCode, String body) {
    super("PagBank error status=" + statusCode);
    this.statusCode = statusCode;
    this.body = body;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getBody() {
    return body;
  }
}
