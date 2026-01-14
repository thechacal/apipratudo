package com.apipratudo.federal.scraper;

import com.apipratudo.federal.dto.PremioDTO;
import java.util.List;

public record ScrapedFederalResult(
    String concurso,
    String dataApuracaoRaw,
    List<PremioDTO> premios
) {
}
