package com.apipratudo.federal.dto;

import java.time.Instant;
import java.util.List;

public record ResultadoOficialResponse(
    String fonte,
    String loteria,
    String concurso,
    String dataApuracao,
    List<PremioDTO> premios,
    Instant capturadoEm
) {
}
