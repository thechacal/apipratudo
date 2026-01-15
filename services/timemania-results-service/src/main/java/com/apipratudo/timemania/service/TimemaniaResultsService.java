package com.apipratudo.timemania.service;

import com.apipratudo.timemania.dto.TimemaniaResultadoOficialResponse;
import com.apipratudo.timemania.error.UpstreamBadResponseException;
import com.apipratudo.timemania.error.UpstreamTimeoutException;
import com.apipratudo.timemania.scraper.CaixaTimemaniaScraper;
import com.apipratudo.timemania.scraper.ScrapedTimemaniaResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TimemaniaResultsService {

  private static final Logger log = LoggerFactory.getLogger(TimemaniaResultsService.class);

  private final CaixaTimemaniaScraper scraper;
  private final Clock clock;

  public TimemaniaResultsService(CaixaTimemaniaScraper scraper, Clock clock) {
    this.scraper = scraper;
    this.clock = clock;
  }

  public TimemaniaResultadoOficialResponse obterResultadoOficial(String traceId) {
    Exception last = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      Instant start = Instant.now(clock);
      try {
        ScrapedTimemaniaResult scraped = scraper.scrape();
        String dataApuracao = TimemaniaDateParser.normalize(scraped.dataApuracaoRaw());
        if (dataApuracao == null) {
          throw new UpstreamBadResponseException("Data de apuracao invalida",
              List.of("Data de apuracao nao encontrada"));
        }
        if (scraped.dezenas() == null || scraped.dezenas().size() != 7) {
          throw new UpstreamBadResponseException("Dezenas incompletas",
              List.of("Elemento de resultado nao encontrado"));
        }
        if (scraped.timeCoracao() == null || scraped.timeCoracao().isBlank()) {
          throw new UpstreamBadResponseException("Time do coracao nao encontrado",
              List.of("Elemento de resultado nao encontrado"));
        }
        TimemaniaResultadoOficialResponse response = new TimemaniaResultadoOficialResponse(
            "CAIXA",
            "TIMEMANIA",
            scraped.concurso(),
            dataApuracao,
            scraped.dezenas(),
            scraped.timeCoracao(),
            Instant.now(clock)
        );
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.info("Timemania scrape ok attempt={} ms={} traceId={}", attempt, ms, safeTraceId(traceId));
        return response;
      } catch (UpstreamTimeoutException | UpstreamBadResponseException ex) {
        last = ex;
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.warn("Timemania scrape failed attempt={} ms={} traceId={} error={}", attempt, ms,
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
