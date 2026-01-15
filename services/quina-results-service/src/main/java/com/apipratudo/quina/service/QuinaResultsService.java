package com.apipratudo.quina.service;

import com.apipratudo.quina.dto.QuinaResultadoOficialResponse;
import com.apipratudo.quina.error.UpstreamBadResponseException;
import com.apipratudo.quina.error.UpstreamTimeoutException;
import com.apipratudo.quina.scraper.CaixaQuinaScraper;
import com.apipratudo.quina.scraper.ScrapedQuinaResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuinaResultsService {

  private static final Logger log = LoggerFactory.getLogger(QuinaResultsService.class);

  private final CaixaQuinaScraper scraper;
  private final Clock clock;

  public QuinaResultsService(CaixaQuinaScraper scraper, Clock clock) {
    this.scraper = scraper;
    this.clock = clock;
  }

  public QuinaResultadoOficialResponse obterResultadoOficial(String traceId) {
    Exception last = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      Instant start = Instant.now(clock);
      try {
        ScrapedQuinaResult scraped = scraper.scrape();
        String dataApuracao = QuinaDateParser.normalize(scraped.dataApuracaoRaw());
        if (dataApuracao == null) {
          throw new UpstreamBadResponseException("Data de apuracao invalida",
              List.of("Data de apuracao nao encontrada"));
        }
        if (scraped.dezenas() == null || scraped.dezenas().size() != 5) {
          throw new UpstreamBadResponseException("Dezenas incompletas",
              List.of("Elemento de resultado nao encontrado"));
        }
        QuinaResultadoOficialResponse response = new QuinaResultadoOficialResponse(
            "CAIXA",
            "QUINA",
            scraped.concurso(),
            dataApuracao,
            scraped.dezenas(),
            Instant.now(clock)
        );
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.info("Quina scrape ok attempt={} ms={} traceId={}", attempt, ms, safeTraceId(traceId));
        return response;
      } catch (UpstreamTimeoutException | UpstreamBadResponseException ex) {
        last = ex;
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.warn("Quina scrape failed attempt={} ms={} traceId={} error={}", attempt, ms,
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
