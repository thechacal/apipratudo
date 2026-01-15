package com.apipratudo.lotofacil.scraper;

import java.util.List;

public record ScrapedLotofacilResult(
    String concurso,
    String dataApuracaoRaw,
    List<String> dezenas
) {
}
