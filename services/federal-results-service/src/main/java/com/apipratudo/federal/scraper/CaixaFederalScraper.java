package com.apipratudo.federal.scraper;

import com.apipratudo.federal.config.PlaywrightConfig;
import com.apipratudo.federal.dto.PremioDTO;
import com.apipratudo.federal.error.UpstreamBadResponseException;
import com.apipratudo.federal.error.UpstreamTimeoutException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;
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
public class CaixaFederalScraper {

  private static final String URL = "https://loterias.caixa.gov.br/Paginas/Federal.aspx";
  private static final Pattern HEADER_RX = Pattern.compile("Concurso\\s*(\\d+)\\s*\\((\\d{2}/\\d{2}/\\d{4})\\)",
      Pattern.CASE_INSENSITIVE);
  private static final List<String> ROW_SELECTORS = List.of(
      "table.resultado-table tbody tr",
      "table.tabela-resultado tbody tr",
      "#resultados tbody tr"
  );
  private static final List<String> BLOCKED_TYPES = List.of("image", "media", "font");

  private final PlaywrightConfig config;

  public CaixaFederalScraper(PlaywrightConfig config) {
    this.config = config;
  }

  public ScrapedFederalResult scrape() {
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
          page.waitForLoadState(LoadState.NETWORKIDLE);

          List<ElementHandle> rows = findRows(page);
          if (rows.isEmpty()) {
            rows = findRowsInFrames(page.frames());
          }
          if (rows.isEmpty()) {
            throw new UpstreamBadResponseException("Elemento de resultado nao encontrado",
                List.of("Elemento de resultado nao encontrado"));
          }

          String titulo = extractHeaderText(page);
          Matcher matcher = HEADER_RX.matcher(titulo);
          if (!matcher.find()) {
            for (Frame frame : page.frames()) {
              titulo = extractHeaderText(frame);
              matcher = HEADER_RX.matcher(titulo);
              if (matcher.find()) {
                break;
              }
            }
          }
          if (!matcher.find()) {
            throw new UpstreamBadResponseException("Cabecalho do concurso nao encontrado",
                List.of("Cabecalho do concurso nao encontrado"));
          }

          String concurso = matcher.group(1);
          String dataApuracao = matcher.group(2);
          List<PremioDTO> premios = extractPremios(rows);

          if (premios.size() != 5) {
            throw new UpstreamBadResponseException("Tabela de resultados incompleta",
                List.of("Tabela de resultados incompleta"));
          }

          return new ScrapedFederalResult(concurso, dataApuracao, premios);
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

  private List<ElementHandle> findRows(Page page) {
    for (String selector : ROW_SELECTORS) {
      try {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
            .setTimeout((double) config.getTimeoutMs()));
        List<ElementHandle> rows = page.querySelectorAll(selector);
        if (rows.size() >= 5) {
          return rows;
        }
      } catch (Exception ignored) {
        // try next selector
      }
    }
    return findGenericRows(page.querySelectorAll("table tbody tr"));
  }

  private List<ElementHandle> findGenericRows(List<ElementHandle> rows) {
    List<ElementHandle> filtered = new ArrayList<>();
    for (ElementHandle row : rows) {
      try {
        List<ElementHandle> cols = row.querySelectorAll("td");
        if (cols.size() >= 5) {
          filtered.add(row);
        }
      } catch (Exception ignored) {
        // ignore row
      }
      if (filtered.size() >= 5) {
        break;
      }
    }
    if (filtered.size() >= 5) {
      return filtered;
    }
    return List.of();
  }

  private List<ElementHandle> findRows(Frame frame) {
    for (String selector : ROW_SELECTORS) {
      try {
        frame.waitForSelector(selector, new Frame.WaitForSelectorOptions()
            .setTimeout((double) config.getTimeoutMs()));
        List<ElementHandle> rows = frame.querySelectorAll(selector);
        if (rows.size() >= 5) {
          return rows;
        }
      } catch (Exception ignored) {
        // try next selector
      }
    }
    return findGenericRows(frame.querySelectorAll("table tbody tr"));
  }

  private List<ElementHandle> findRowsInFrames(List<Frame> frames) {
    for (Frame frame : frames) {
      List<ElementHandle> rows = findRows(frame);
      if (!rows.isEmpty()) {
        return rows;
      }
    }
    return List.of();
  }

  private String extractHeaderText(Page page) {
    try {
      List<ElementHandle> headers = page.querySelectorAll("h2, h3");
      for (ElementHandle header : headers) {
        String text = safeText(header);
        if (HEADER_RX.matcher(text).find()) {
          return text;
        }
      }
    } catch (Exception ignored) {
      return "";
    }
    return "";
  }

  private String extractHeaderText(Frame frame) {
    try {
      List<ElementHandle> headers = frame.querySelectorAll("h2, h3");
      for (ElementHandle header : headers) {
        String text = safeText(header);
        if (HEADER_RX.matcher(text).find()) {
          return text;
        }
      }
    } catch (Exception ignored) {
      return "";
    }
    return "";
  }

  private List<PremioDTO> extractPremios(List<ElementHandle> rows) {
    List<PremioDTO> premios = new ArrayList<>();
    for (ElementHandle row : rows) {
      List<ElementHandle> cols = row.querySelectorAll("td");
      if (cols.size() < 5) {
        continue;
      }
      PremioDTO premio = new PremioDTO(
          safeText(cols.get(0)),
          safeText(cols.get(1)),
          safeText(cols.get(2)),
          safeText(cols.get(3)),
          safeText(cols.get(4))
      );
      premios.add(premio);
      if (premios.size() >= 5) {
        break;
      }
    }
    return premios;
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
