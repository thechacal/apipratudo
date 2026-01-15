package com.apipratudo.diadesorte.error;

import java.util.List;

public class UpstreamBadResponseException extends RuntimeException {

  private final List<String> details;

  public UpstreamBadResponseException(String message, List<String> details) {
    super(message);
    this.details = details;
  }

  public List<String> getDetails() {
    return details;
  }
}
