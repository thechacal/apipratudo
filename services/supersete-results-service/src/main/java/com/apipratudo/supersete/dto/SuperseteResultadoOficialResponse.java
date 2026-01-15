package com.apipratudo.supersete.dto;

import java.time.Instant;
import java.util.List;

public record SuperseteResultadoOficialResponse(
    String fonte,
    String loteria,
    String concurso,
    String dataApuracao,
    List<String> colunas,
    Instant capturadoEm
) {
}
