package com.apipratudo.megasena.dto;

import java.time.Instant;
import java.util.List;

public record MegasenaResultadoOficialResponse(
    String fonte,
    String loteria,
    String concurso,
    String dataApuracao,
    List<String> dezenas,
    Instant capturadoEm
) {
}
