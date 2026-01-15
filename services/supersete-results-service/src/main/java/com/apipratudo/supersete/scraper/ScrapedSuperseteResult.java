package com.apipratudo.supersete.scraper;

import java.util.List;

public record ScrapedSuperseteResult(
    String concurso,
    String dataApuracaoRaw,
    List<String> colunas
) {
}
