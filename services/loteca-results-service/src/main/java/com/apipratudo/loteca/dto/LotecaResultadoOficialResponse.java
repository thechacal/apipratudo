package com.apipratudo.loteca.dto;

import java.time.Instant;
import java.util.List;

public record LotecaResultadoOficialResponse(
    String fonte,
    String loteria,
    String concurso,
    String dataApuracao,
    List<LotecaJogoDTO> jogos,
    Instant capturadoEm
) {
}
