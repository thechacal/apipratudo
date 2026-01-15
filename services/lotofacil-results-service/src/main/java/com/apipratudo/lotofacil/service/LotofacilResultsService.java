package com.apipratudo.lotofacil.service;

import com.apipratudo.lotofacil.dto.LotofacilResultadoOficialResponse;
import com.apipratudo.lotofacil.error.UpstreamBadResponseException;
import com.apipratudo.lotofacil.error.UpstreamTimeoutException;
import com.apipratudo.lotofacil.scraper.CaixaLotofacilScraper;
import com.apipratudo.lotofacil.scraper.ScrapedLotofacilResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LotofacilResultsService {

  private static final Logger log = LoggerFactory.getLogger(LotofacilResultsService.class);

  private final CaixaLotofacilScraper scraper;
  private final Clock clock;

  public LotofacilResultsService(CaixaLotofacilScraper scraper, Clock clock) {
    this.scraper = scraper;
    this.clock = clock;
  }

  public LotofacilResultadoOficialResponse obterResultadoOficial(String traceId) {
    Exception last = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      Instant start = Instant.now(clock);
      try {
        ScrapedLotofacilResult scraped = scraper.scrape();
        String dataApuracao = LotofacilDateParser.normalize(scraped.dataApuracaoRaw());
        if (dataApuracao == null) {
          throw new UpstreamBadResponseException("Data de apuracao invalida",
              List.of("Data de apuracao nao encontrada"));
        }
        if (scraped.dezenas() == null || scraped.dezenas().size() != 15) {
          throw new UpstreamBadResponseException("Dezenas incompletas",
              List.of("Elemento de resultado nao encontrado"));
        }
        LotofacilResultadoOficialResponse response = new LotofacilResultadoOficialResponse(
            "CAIXA",
            "LOTOFACIL",
            scraped.concurso(),
            dataApuracao,
            scraped.dezenas(),
            Instant.now(clock)
        );
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.info("Lotofacil scrape ok attempt={} ms={} traceId={}", attempt, ms, safeTraceId(traceId));
        return response;
      } catch (UpstreamTimeoutException | UpstreamBadResponseException ex) {
        last = ex;
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.warn("Lotofacil scrape failed attempt={} ms={} traceId={} error={}", attempt, ms,
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
