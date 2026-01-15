package com.apipratudo.timemania.dto;

import java.time.Instant;
import java.util.List;

public record TimemaniaResultadoOficialResponse(
    String fonte,
    String loteria,
    String concurso,
    String dataApuracao,
    List<String> dezenas,
    String timeCoracao,
    Instant capturadoEm
) {
}
