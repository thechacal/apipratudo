package com.apipratudo.loteca.scraper;

import com.apipratudo.loteca.dto.LotecaJogoDTO;
import java.util.List;

public record ScrapedLotecaResult(
    String concurso,
    String dataApuracaoRaw,
    List<LotecaJogoDTO> jogos
) {
}
