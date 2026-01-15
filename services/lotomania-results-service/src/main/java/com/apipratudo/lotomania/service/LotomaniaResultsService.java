package com.apipratudo.lotomania.service;

import com.apipratudo.lotomania.dto.LotomaniaResultadoOficialResponse;
import com.apipratudo.lotomania.error.UpstreamBadResponseException;
import com.apipratudo.lotomania.error.UpstreamTimeoutException;
import com.apipratudo.lotomania.scraper.CaixaLotomaniaScraper;
import com.apipratudo.lotomania.scraper.ScrapedLotomaniaResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LotomaniaResultsService {

  private static final Logger log = LoggerFactory.getLogger(LotomaniaResultsService.class);

  private final CaixaLotomaniaScraper scraper;
  private final Clock clock;

  public LotomaniaResultsService(CaixaLotomaniaScraper scraper, Clock clock) {
    this.scraper = scraper;
    this.clock = clock;
  }

  public LotomaniaResultadoOficialResponse obterResultadoOficial(String traceId) {
    Exception last = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      Instant start = Instant.now(clock);
      try {
        ScrapedLotomaniaResult scraped = scraper.scrape();
        String dataApuracao = LotomaniaDateParser.normalize(scraped.dataApuracaoRaw());
        if (dataApuracao == null) {
          throw new UpstreamBadResponseException("Data de apuracao invalida",
              List.of("Data de apuracao nao encontrada"));
        }
        if (scraped.dezenas() == null || scraped.dezenas().size() != 20) {
          throw new UpstreamBadResponseException("Dezenas incompletas",
              List.of("Elemento de resultado nao encontrado"));
        }
        LotomaniaResultadoOficialResponse response = new LotomaniaResultadoOficialResponse(
            "CAIXA",
            "LOTOMANIA",
            scraped.concurso(),
            dataApuracao,
            scraped.dezenas(),
            Instant.now(clock)
        );
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.info("Lotomania scrape ok attempt={} ms={} traceId={}", attempt, ms, safeTraceId(traceId));
        return response;
      } catch (UpstreamTimeoutException | UpstreamBadResponseException ex) {
        last = ex;
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.warn("Lotomania scrape failed attempt={} ms={} traceId={} error={}", attempt, ms,
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
