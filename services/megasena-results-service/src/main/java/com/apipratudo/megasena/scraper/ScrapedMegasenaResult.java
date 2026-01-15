package com.apipratudo.megasena.scraper;

import java.util.List;

public record ScrapedMegasenaResult(
    String concurso,
    String dataApuracaoRaw,
    List<String> dezenas
) {
}
