package com.apipratudo.loteca.scraper;

import com.apipratudo.loteca.config.PlaywrightConfig;
import com.apipratudo.loteca.dto.LotecaJogoDTO;
import com.apipratudo.loteca.error.UpstreamBadResponseException;
import com.apipratudo.loteca.error.UpstreamTimeoutException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CaixaLotecaScraper {

  private static final String URL = "https://loterias.caixa.gov.br/Paginas/Loteca.aspx";
  private static final Pattern HEADER_RX = Pattern.compile("Concurso\\s*(\\d+)\\s*\\((\\d{2}/\\d{2}/\\d{4})\\)",
      Pattern.CASE_INSENSITIVE);
  private static final List<String> HEADER_SELECTORS = List.of(
      "h2",
      ".titulo-modalidade",
      "#wp_resultados h2"
  );
  private static final List<String> ROW_SELECTORS = List.of(
      "table.resultado-table tbody tr",
      "table.tabela-resultado tbody tr",
      "#resultados tbody tr",
      "table tbody tr"
  );
  private static final List<String> READY_HINTS = List.of(
      "table",
      "#resultados",
      "h2"
  );
  private static final List<String> BLOCKED_TYPES = List.of("image", "media", "font");

  private final PlaywrightConfig config;

  public CaixaLotecaScraper(PlaywrightConfig config) {
    this.config = config;
  }

  public ScrapedLotecaResult scrape() {
    try (Playwright playwright = Playwright.create()) {
      try (Browser browser = playwright.chromium().launch(new LaunchOptions()
          .setHeadless(true)
          .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage")))) {
        try (var context = browser.newContext(new NewContextOptions().setUserAgent(config.getUserAgent()))) {
          Page page = context.newPage();
          page.setDefaultTimeout(config.getTimeoutMs());
          page.setDefaultNavigationTimeout(config.getNavigationTimeoutMs());
          page.route("**/*", route -> {
            String type = route.request().resourceType();
            if (BLOCKED_TYPES.contains(type)) {
              route.abort();
              return;
            }
            route.resume();
          });

          page.navigate(URL, new Page.NavigateOptions()
              .setTimeout((double) config.getNavigationTimeoutMs())
              .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
          page.waitForLoadState(LoadState.DOMCONTENTLOADED);

          waitForReady(page);

          String header = findHeader(page);
          if (header == null || header.isBlank()) {
            throw new UpstreamBadResponseException("Cabecalho do concurso nao encontrado",
                List.of("Cabecalho do concurso nao encontrado"));
          }

          Matcher matcher = HEADER_RX.matcher(header);
          if (!matcher.find()) {
            throw new UpstreamBadResponseException("Cabecalho do concurso nao encontrado",
                List.of("Cabecalho do concurso nao encontrado"));
          }

          String concurso = matcher.group(1);
          String dataApuracao = matcher.group(2);

          List<LotecaJogoDTO> jogos = findJogos(page);
          if (jogos.isEmpty()) {
            throw new UpstreamBadResponseException("Jogos incompletos",
                List.of("Elemento de resultado nao encontrado"));
          }

          return new ScrapedLotecaResult(concurso, dataApuracao, jogos);
        }
      }
    } catch (TimeoutError ex) {
      throw new UpstreamTimeoutException("Timeout ao consultar resultado oficial da CAIXA", ex);
    } catch (UpstreamBadResponseException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new UpstreamBadResponseException("Falha ao consultar resultado oficial da CAIXA",
          List.of("Elemento de resultado nao encontrado"));
    }
  }

  private void waitForReady(Page page) {
    for (String selector : READY_HINTS) {
      try {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
            .setTimeout((double) config.getTimeoutMs()));
        return;
      } catch (Exception ignored) {
        // try next
      }
    }
    throw new UpstreamBadResponseException("Elemento de resultado nao encontrado",
        List.of("Elemento de resultado nao encontrado"));
  }

  private String findHeader(Page page) {
    for (String selector : HEADER_SELECTORS) {
      try {
        String text = page.locator(selector).first().innerText();
        if (text != null && !text.isBlank()) {
          return text.trim();
        }
      } catch (Exception ignored) {
        // try next
      }
    }
    return null;
  }

  private List<LotecaJogoDTO> findJogos(Page page) {
    for (String selector : ROW_SELECTORS) {
      List<ElementHandle> rows = page.querySelectorAll(selector);
      List<LotecaJogoDTO> jogos = parseRows(rows);
      if (!jogos.isEmpty()) {
        return jogos;
      }
    }
    return List.of();
  }

  private List<LotecaJogoDTO> parseRows(List<ElementHandle> rows) {
    List<LotecaJogoDTO> jogos = new ArrayList<>();
    int fallbackIndex = 1;
    for (ElementHandle row : rows) {
      List<ElementHandle> cols = row.querySelectorAll("td");
      if (cols.size() < 5) {
        continue;
      }
      String jogoRaw = safeText(cols.get(0));
      String time1 = safeText(cols.get(1));
      String gols1 = safeText(cols.get(2));
      String time2 = safeText(cols.get(3));
      String gols2 = safeText(cols.get(4));
      if (time1.isBlank() || time2.isBlank()) {
        continue;
      }
      int jogo = parseJogo(jogoRaw, fallbackIndex);
      jogos.add(new LotecaJogoDTO(jogo, time1, gols1, time2, gols2));
      fallbackIndex++;
    }
    return jogos;
  }

  private int parseJogo(String raw, int fallback) {
    if (raw == null) {
      return fallback;
    }
    try {
      return Integer.parseInt(raw.replaceAll("[^0-9]", ""));
    } catch (Exception ex) {
      return fallback;
    }
  }

  private String safeText(ElementHandle handle) {
    if (handle == null) {
      return "";
    }
    try {
      return handle.innerText().trim();
    } catch (Exception ex) {
      return "";
    }
  }
}
