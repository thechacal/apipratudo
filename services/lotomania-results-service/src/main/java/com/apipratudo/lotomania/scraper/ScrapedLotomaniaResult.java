package com.apipratudo.lotomania.scraper;

import java.util.List;

public record ScrapedLotomaniaResult(
    String concurso,
    String dataApuracaoRaw,
    List<String> dezenas
) {
}
