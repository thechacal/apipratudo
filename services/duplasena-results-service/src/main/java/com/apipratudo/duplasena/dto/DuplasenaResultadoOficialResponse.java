package com.apipratudo.duplasena.dto;

import java.time.Instant;
import java.util.List;

public record DuplasenaResultadoOficialResponse(
    String fonte,
    String loteria,
    String concurso,
    String dataApuracao,
    List<String> sorteio1,
    List<String> sorteio2,
    Instant capturadoEm
) {
}
