package com.apipratudo.duplasena.scraper;

import java.util.List;

public record ScrapedDuplasenaResult(
    String concurso,
    String dataApuracaoRaw,
    List<String> sorteio1,
    List<String> sorteio2
) {
}
