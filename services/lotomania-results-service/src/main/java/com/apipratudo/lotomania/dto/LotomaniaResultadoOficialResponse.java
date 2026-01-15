package com.apipratudo.lotomania.dto;

import java.time.Instant;
import java.util.List;

public record LotomaniaResultadoOficialResponse(
    String fonte,
    String loteria,
    String concurso,
    String dataApuracao,
    List<String> dezenas,
    Instant capturadoEm
) {
}
