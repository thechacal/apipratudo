package com.apipratudo.diadesorte.service;

import com.apipratudo.diadesorte.dto.DiadesorteResultadoOficialResponse;
import com.apipratudo.diadesorte.error.UpstreamBadResponseException;
import com.apipratudo.diadesorte.error.UpstreamTimeoutException;
import com.apipratudo.diadesorte.scraper.CaixaDiadesorteScraper;
import com.apipratudo.diadesorte.scraper.ScrapedDiadesorteResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiadesorteResultsService {

  private static final Logger log = LoggerFactory.getLogger(DiadesorteResultsService.class);

  private final CaixaDiadesorteScraper scraper;
  private final Clock clock;

  public DiadesorteResultsService(CaixaDiadesorteScraper scraper, Clock clock) {
    this.scraper = scraper;
    this.clock = clock;
  }

  public DiadesorteResultadoOficialResponse obterResultadoOficial(String traceId) {
    Exception last = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      Instant start = Instant.now(clock);
      try {
        ScrapedDiadesorteResult scraped = scraper.scrape();
        String dataApuracao = DiadesorteDateParser.normalize(scraped.dataApuracaoRaw());
        if (dataApuracao == null) {
          throw new UpstreamBadResponseException("Data de apuracao invalida",
              List.of("Data de apuracao nao encontrada"));
        }
        if (scraped.dezenas() == null || scraped.dezenas().size() != 7) {
          throw new UpstreamBadResponseException("Dezenas incompletas",
              List.of("Elemento de resultado nao encontrado"));
        }
        if (scraped.mesDaSorte() == null || scraped.mesDaSorte().isBlank()) {
          throw new UpstreamBadResponseException("Mes da sorte nao encontrado",
              List.of("Elemento de resultado nao encontrado"));
        }
        DiadesorteResultadoOficialResponse response = new DiadesorteResultadoOficialResponse(
            "CAIXA",
            "DIADESORTE",
            scraped.concurso(),
            dataApuracao,
            scraped.dezenas(),
            scraped.mesDaSorte(),
            Instant.now(clock)
        );
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.info("Diadesorte scrape ok attempt={} ms={} traceId={}", attempt, ms, safeTraceId(traceId));
        return response;
      } catch (UpstreamTimeoutException | UpstreamBadResponseException ex) {
        last = ex;
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.warn("Diadesorte scrape failed attempt={} ms={} traceId={} error={}", attempt, ms,
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
