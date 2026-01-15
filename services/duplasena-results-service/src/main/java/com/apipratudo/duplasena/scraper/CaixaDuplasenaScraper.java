package com.apipratudo.duplasena.scraper;

import com.apipratudo.duplasena.config.PlaywrightConfig;
import com.apipratudo.duplasena.error.UpstreamBadResponseException;
import com.apipratudo.duplasena.error.UpstreamTimeoutException;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CaixaDuplasenaScraper {

  private static final String URL = "https://loterias.caixa.gov.br/Paginas/Dupla-Sena.aspx";
  private static final Pattern HEADER_RX = Pattern.compile("Concurso\\s*(\\d+)\\s*\\((\\d{2}/\\d{2}/\\d{4})\\)",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern DEZENA_RX = Pattern.compile("\\b(0?[1-9]|[1-4]\\d|50)\\b");
  private static final List<String> HEADER_SELECTORS = List.of(
      "h2",
      ".titulo-modalidade",
      "#wp_resultados h2"
  );
  private static final List<String> DEZENAS_SELECTORS = List.of(
      "#ulDezenas li",
      "ul.lista-dezenas li",
      ".resultado-loteria li",
      ".numbers li",
      ".resultado-loteria .ng-binding"
  );
  private static final List<String> READY_HINTS = List.of(
      "#ulDezenas",
      "ul.lista-dezenas",
      ".resultado-loteria",
      ".numbers",
      "h2"
  );
  private static final List<String> BLOCKED_TYPES = List.of("image", "media", "font");

  private final PlaywrightConfig config;

  public CaixaDuplasenaScraper(PlaywrightConfig config) {
    this.config = config;
  }

  public ScrapedDuplasenaResult scrape() {
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

          Sorteios sorteios = findSorteios(page);
          if (sorteios == null || sorteios.sorteio1().size() != 6 || sorteios.sorteio2().size() != 6) {
            throw new UpstreamBadResponseException("Dezenas incompletas",
                List.of("Elemento de resultado nao encontrado"));
          }

          return new ScrapedDuplasenaResult(concurso, dataApuracao, sorteios.sorteio1(), sorteios.sorteio2());
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

  private Sorteios findSorteios(Page page) {
    for (String selector : DEZENAS_SELECTORS) {
      List<ElementHandle> elements = page.querySelectorAll(selector);
      List<Integer> values = parseDezenas(elements);
      if (values.size() >= 12) {
        List<String> sorteio1 = normalize(values.subList(0, 6));
        List<String> sorteio2 = normalize(values.subList(6, 12));
        if (!sorteio1.isEmpty() && !sorteio2.isEmpty()) {
          return new Sorteios(sorteio1, sorteio2);
        }
      }
    }
    return null;
  }

  private List<Integer> parseDezenas(List<ElementHandle> elements) {
    List<Integer> values = new ArrayList<>();
    for (ElementHandle element : elements) {
      String text = safeText(element);
      if (text.isBlank()) {
        continue;
      }
      Matcher matcher = DEZENA_RX.matcher(text);
      while (matcher.find()) {
        int value = Integer.parseInt(matcher.group(1));
        if (value >= 1 && value <= 50) {
          values.add(value);
        }
      }
    }
    return values;
  }

  private List<String> normalize(List<Integer> raw) {
    Set<Integer> values = new TreeSet<>();
    for (Integer value : raw) {
      if (value != null && value >= 1 && value <= 50) {
        values.add(value);
      }
    }
    if (values.size() != 6) {
      return List.of();
    }
    List<String> dezenas = new ArrayList<>(6);
    for (int value : values) {
      dezenas.add(String.format("%02d", value));
    }
    return dezenas;
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

  private record Sorteios(List<String> sorteio1, List<String> sorteio2) {
  }
}
