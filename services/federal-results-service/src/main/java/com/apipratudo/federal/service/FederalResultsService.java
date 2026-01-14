package com.apipratudo.federal.service;

import com.apipratudo.federal.dto.PremioDTO;
import com.apipratudo.federal.dto.ResultadoOficialResponse;
import com.apipratudo.federal.error.UpstreamBadResponseException;
import com.apipratudo.federal.error.UpstreamTimeoutException;
import com.apipratudo.federal.scraper.CaixaFederalScraper;
import com.apipratudo.federal.scraper.ScrapedFederalResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FederalResultsService {

  private static final Logger log = LoggerFactory.getLogger(FederalResultsService.class);

  private final CaixaFederalScraper scraper;
  private final Clock clock;

  public FederalResultsService(CaixaFederalScraper scraper, Clock clock) {
    this.scraper = scraper;
    this.clock = clock;
  }

  public ResultadoOficialResponse obterResultadoOficial(String traceId) {
    Exception last = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      Instant start = Instant.now(clock);
      try {
        ScrapedFederalResult scraped = scraper.scrape();
        String dataApuracao = FederalDateParser.normalize(scraped.dataApuracaoRaw());
        if (dataApuracao == null) {
          throw new UpstreamBadResponseException("Data de apuracao invalida",
              List.of("Data de apuracao nao encontrada"));
        }
        ResultadoOficialResponse response = new ResultadoOficialResponse(
            "CAIXA",
            "FEDERAL",
            scraped.concurso(),
            dataApuracao,
            scraped.premios(),
            Instant.now(clock)
        );
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.info("Federal scrape ok attempt={} ms={} traceId={}", attempt, ms, safeTraceId(traceId));
        return response;
      } catch (UpstreamTimeoutException | UpstreamBadResponseException ex) {
        last = ex;
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.warn("Federal scrape failed attempt={} ms={} traceId={} error={}", attempt, ms, safeTraceId(traceId),
            ex.getMessage());
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
