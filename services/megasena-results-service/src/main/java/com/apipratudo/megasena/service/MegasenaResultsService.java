package com.apipratudo.megasena.service;

import com.apipratudo.megasena.dto.MegasenaResultadoOficialResponse;
import com.apipratudo.megasena.error.UpstreamBadResponseException;
import com.apipratudo.megasena.error.UpstreamTimeoutException;
import com.apipratudo.megasena.scraper.CaixaMegasenaScraper;
import com.apipratudo.megasena.scraper.ScrapedMegasenaResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MegasenaResultsService {

  private static final Logger log = LoggerFactory.getLogger(MegasenaResultsService.class);

  private final CaixaMegasenaScraper scraper;
  private final Clock clock;

  public MegasenaResultsService(CaixaMegasenaScraper scraper, Clock clock) {
    this.scraper = scraper;
    this.clock = clock;
  }

  public MegasenaResultadoOficialResponse obterResultadoOficial(String traceId) {
    Exception last = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      Instant start = Instant.now(clock);
      try {
        ScrapedMegasenaResult scraped = scraper.scrape();
        String dataApuracao = MegasenaDateParser.normalize(scraped.dataApuracaoRaw());
        if (dataApuracao == null) {
          throw new UpstreamBadResponseException("Data de apuracao invalida",
              List.of("Data de apuracao nao encontrada"));
        }
        if (scraped.dezenas() == null || scraped.dezenas().size() != 6) {
          throw new UpstreamBadResponseException("Dezenas incompletas",
              List.of("Elemento de resultado nao encontrado"));
        }
        MegasenaResultadoOficialResponse response = new MegasenaResultadoOficialResponse(
            "CAIXA",
            "MEGA_SENA",
            scraped.concurso(),
            dataApuracao,
            scraped.dezenas(),
            Instant.now(clock)
        );
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.info("Megasena scrape ok attempt={} ms={} traceId={}", attempt, ms, safeTraceId(traceId));
        return response;
      } catch (UpstreamTimeoutException | UpstreamBadResponseException ex) {
        last = ex;
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.warn("Megasena scrape failed attempt={} ms={} traceId={} error={}", attempt, ms,
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
