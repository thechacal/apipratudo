package com.apipratudo.scheduling.dto;

import java.util.List;

public record SlotsResponse(
    String serviceId,
    String date,
    String agendaId,
    int slotStepMin,
    List<SlotResponse> slots
) {
}
