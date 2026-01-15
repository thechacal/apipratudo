package com.apipratudo.timemania.scraper;

import java.util.List;

public record ScrapedTimemaniaResult(
    String concurso,
    String dataApuracaoRaw,
    List<String> dezenas,
    String timeCoracao
) {
}
