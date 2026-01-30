package com.apipratudo.scheduling.dto;

public record AgendaCreditsStatusResponse(
    String agendaId,
    String chargeId,
    String status,
    long creditsAdded,
    long creditsBalance
) {
}
