package com.apipratudo.maismilionaria.scraper;

import java.util.List;

public record ScrapedMaismilionariaResult(
    String concurso,
    String dataApuracaoRaw,
    List<String> dezenas,
    List<String> trevos
) {
}
