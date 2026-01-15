package com.apipratudo.diadesorte.dto;

import java.time.Instant;
import java.util.List;

public record DiadesorteResultadoOficialResponse(
    String fonte,
    String loteria,
    String concurso,
    String dataApuracao,
    List<String> dezenas,
    String mesDaSorte,
    Instant capturadoEm
) {
}
