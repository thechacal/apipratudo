package com.apipratudo.scheduling.error;

import org.springframework.http.HttpStatus;

public class SlotUnavailableException extends ApiException {

  public SlotUnavailableException() {
    super(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "Slot unavailable");
  }
}
