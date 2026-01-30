package com.apipratudo.scheduling.error;

import org.springframework.http.HttpStatus;

public class AgendaCreditsExceededException extends ApiException {

  public AgendaCreditsExceededException() {
    super(
        HttpStatus.PAYMENT_REQUIRED,
        "AGENDA_CREDITS_EXCEEDED",
        "Creditos da agenda esgotados. Recarregue para continuar."
    );
  }
}
