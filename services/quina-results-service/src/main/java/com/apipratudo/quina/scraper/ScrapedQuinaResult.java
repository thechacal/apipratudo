package com.apipratudo.quina.scraper;

import java.util.List;

public record ScrapedQuinaResult(
    String concurso,
    String dataApuracaoRaw,
    List<String> dezenas
) {
}
