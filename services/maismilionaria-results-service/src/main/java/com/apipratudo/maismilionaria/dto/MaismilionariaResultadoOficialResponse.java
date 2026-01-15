package com.apipratudo.maismilionaria.dto;

import java.time.Instant;
import java.util.List;

public record MaismilionariaResultadoOficialResponse(
    String fonte,
    String loteria,
    String concurso,
    String dataApuracao,
    List<String> dezenas,
    List<String> trevos,
    Instant capturadoEm
) {
}
