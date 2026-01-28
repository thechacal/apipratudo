package com.apipratudo.scheduling.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ReservationRequest(
    @NotBlank String serviceId,
    @NotBlank String agendaId,
    @NotNull Instant startAt,
    @NotNull @Valid CustomerRequest customer,
    String notes
) {
}
