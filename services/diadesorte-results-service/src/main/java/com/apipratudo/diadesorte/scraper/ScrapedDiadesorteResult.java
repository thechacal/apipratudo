package com.apipratudo.diadesorte.scraper;

import java.util.List;

public record ScrapedDiadesorteResult(
    String concurso,
    String dataApuracaoRaw,
    List<String> dezenas,
    String mesDaSorte
) {
}
