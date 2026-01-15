package com.apipratudo.loteca.service;

import com.apipratudo.loteca.dto.LotecaResultadoOficialResponse;
import com.apipratudo.loteca.error.UpstreamBadResponseException;
import com.apipratudo.loteca.error.UpstreamTimeoutException;
import com.apipratudo.loteca.scraper.CaixaLotecaScraper;
import com.apipratudo.loteca.scraper.ScrapedLotecaResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LotecaResultsService {

  private static final Logger log = LoggerFactory.getLogger(LotecaResultsService.class);

  private final CaixaLotecaScraper scraper;
  private final Clock clock;

  public LotecaResultsService(CaixaLotecaScraper scraper, Clock clock) {
    this.scraper = scraper;
    this.clock = clock;
  }

  public LotecaResultadoOficialResponse obterResultadoOficial(String traceId) {
    Exception last = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      Instant start = Instant.now(clock);
      try {
        ScrapedLotecaResult scraped = scraper.scrape();
        String dataApuracao = LotecaDateParser.normalize(scraped.dataApuracaoRaw());
        if (dataApuracao == null) {
          throw new UpstreamBadResponseException("Data de apuracao invalida",
              List.of("Data de apuracao nao encontrada"));
        }
        if (scraped.jogos() == null || scraped.jogos().isEmpty()) {
          throw new UpstreamBadResponseException("Jogos incompletos",
              List.of("Elemento de resultado nao encontrado"));
        }
        LotecaResultadoOficialResponse response = new LotecaResultadoOficialResponse(
            "CAIXA",
            "LOTECA",
            scraped.concurso(),
            dataApuracao,
            scraped.jogos(),
            Instant.now(clock)
        );
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.info("Loteca scrape ok attempt={} ms={} traceId={}", attempt, ms, safeTraceId(traceId));
        return response;
      } catch (UpstreamTimeoutException | UpstreamBadResponseException ex) {
        last = ex;
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.warn("Loteca scrape failed attempt={} ms={} traceId={} error={}", attempt, ms,
            safeTraceId(traceId), ex.getMessage());
      }
    }

    if (last instanceof UpstreamTimeoutException timeout) {
      throw timeout;
    }
    if (last instanceof UpstreamBadResponseException badResponse) {
      throw badResponse;
    }
    throw new UpstreamBadResponseException("Falha ao consultar resultado oficial da CAIXA",
        List.of("Erro inesperado"));
  }

  private String safeTraceId(String traceId) {
    return traceId == null || traceId.isBlank() ? "-" : traceId;
  }
}
