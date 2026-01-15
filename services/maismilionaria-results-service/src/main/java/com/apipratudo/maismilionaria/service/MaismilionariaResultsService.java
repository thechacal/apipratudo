package com.apipratudo.maismilionaria.service;

import com.apipratudo.maismilionaria.dto.MaismilionariaResultadoOficialResponse;
import com.apipratudo.maismilionaria.error.UpstreamBadResponseException;
import com.apipratudo.maismilionaria.error.UpstreamTimeoutException;
import com.apipratudo.maismilionaria.scraper.CaixaMaismilionariaScraper;
import com.apipratudo.maismilionaria.scraper.ScrapedMaismilionariaResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MaismilionariaResultsService {

  private static final Logger log = LoggerFactory.getLogger(MaismilionariaResultsService.class);

  private final CaixaMaismilionariaScraper scraper;
  private final Clock clock;

  public MaismilionariaResultsService(CaixaMaismilionariaScraper scraper, Clock clock) {
    this.scraper = scraper;
    this.clock = clock;
  }

  public MaismilionariaResultadoOficialResponse obterResultadoOficial(String traceId) {
    Exception last = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      Instant start = Instant.now(clock);
      try {
        ScrapedMaismilionariaResult scraped = scraper.scrape();
        String dataApuracao = MaismilionariaDateParser.normalize(scraped.dataApuracaoRaw());
        if (dataApuracao == null) {
          throw new UpstreamBadResponseException("Data de apuracao invalida",
              List.of("Data de apuracao nao encontrada"));
        }
        if (scraped.dezenas() == null || scraped.dezenas().size() != 6
            || scraped.trevos() == null || scraped.trevos().size() != 2) {
          throw new UpstreamBadResponseException("Dezenas incompletas",
              List.of("Elemento de resultado nao encontrado"));
        }
        MaismilionariaResultadoOficialResponse response = new MaismilionariaResultadoOficialResponse(
            "CAIXA",
            "MAISMILIONARIA",
            scraped.concurso(),
            dataApuracao,
            scraped.dezenas(),
            scraped.trevos(),
            Instant.now(clock)
        );
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.info("Maismilionaria scrape ok attempt={} ms={} traceId={}", attempt, ms, safeTraceId(traceId));
        return response;
      } catch (UpstreamTimeoutException | UpstreamBadResponseException ex) {
        last = ex;
        long ms = Duration.between(start, Instant.now(clock)).toMillis();
        log.warn("Maismilionaria scrape failed attempt={} ms={} traceId={} error={}", attempt, ms,
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
