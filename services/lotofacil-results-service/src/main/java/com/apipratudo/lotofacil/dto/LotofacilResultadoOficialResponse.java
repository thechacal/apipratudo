package com.apipratudo.lotofacil.dto;

import java.time.Instant;
import java.util.List;

public record LotofacilResultadoOficialResponse(
    String fonte,
    String loteria,
    String concurso,
    String dataApuracao,
    List<String> dezenas,
    Instant capturadoEm
) {
}
