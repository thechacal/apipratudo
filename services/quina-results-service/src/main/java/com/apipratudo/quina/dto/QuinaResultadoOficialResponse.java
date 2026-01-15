package com.apipratudo.quina.dto;

import java.time.Instant;
import java.util.List;

public record QuinaResultadoOficialResponse(
    String fonte,
    String loteria,
    String concurso,
    String dataApuracao,
    List<String> dezenas,
    Instant capturadoEm
) {
}
