package com.apipratudo.scheduling.error;

import org.springframework.http.HttpStatus;

public class HoldExpiredException extends ApiException {

  public HoldExpiredException() {
    super(HttpStatus.CONFLICT, "HOLD_EXPIRED", "Hold expired");
  }
}
