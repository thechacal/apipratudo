package com.apipratudo.duplasena.service;

import com.apipratudo.duplasena.dto.DuplasenaResultadoOficialResponse;
import com.apipratudo.duplasena.error.UpstreamBadResponseException;
import com.apipratudo.duplasena.error.UpstreamTimeoutException;
import com.apipratudo.duplasena.scraper.CaixaDuplasenaScraper;
import com.apipratudo.duplasena.scraper.ScrapedDuplasenaResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DuplasenaResultsService {

  private static final Logger log = LoggerFactory.getLogger(DuplasenaResultsService.class);

  private final CaixaDuplasenaScraper scraper;
  private final Clock clock;

  public DuplasenaResultsService(CaixaDuplasenaScraper scraper, Clock clock) {
    this.scraper = scraper;
    this.clock = clock;
  }

  public DuplasenaResultadoOficialResponse obterResultadoOficial(String traceId) {
    Exception last = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      Instant start = Instant.now(clock);
      try {
        ScrapedDuplasenaResult scraped = scraper.scrape();
        String dataApuracao = DuplasenaDateParser.normalize(scraped.dataApuracaoRaw());
        if (dataApuracao == null) {
          throw new UpstreamBadResponseException("Data de apuracao invalida",
              List.of("Data de apuracao nao encontrada"));
        }
        if (scraped.sorteio1() == null || scraped.sorteio1().size() != 6
            || scraped.sorteio2() == null || scraped.sorteio2().size() != 6) {
          throw new UpstreamBadResponseException("Dezenas incompletas",
              List.of("Elemento de resultado nao encontrado"));
        }
        DuplasenaResultadoOficialResponse response = new DuplasenaResultadoOficialResponse(
            "CAIXA",
            "DUPLASENA",
            scraped.concurso(),
            dataApuracao,
            scraped.sorteio1(),
            scraped.sorteio2(),
            Instant.now(clock)
        );
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.info("Duplasena scrape ok attempt={} ms={} traceId={}", attempt, ms, safeTraceId(traceId));
        return response;
      } catch (UpstreamTimeoutException | UpstreamBadResponseException ex) {
        last = ex;
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.warn("Duplasena scrape failed attempt={} ms={} traceId={} error={}", attempt, ms,
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
