package com.apipratudo.lotomania.scraper;

import com.apipratudo.lotomania.config.PlaywrightConfig;
import com.apipratudo.lotomania.error.UpstreamBadResponseException;
import com.apipratudo.lotomania.error.UpstreamTimeoutException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CaixaLotomaniaScraper {

  private static final String URL = "https://loterias.caixa.gov.br/Paginas/Lotomania.aspx";
  private static final String API_URL = "https://servicebus2.caixa.gov.br/portaldeloterias/api/lotomania";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern HEADER_RX = Pattern.compile("Concurso\\s*(\\d+)\\s*\\((\\d{2}/\\d{2}/\\d{4})\\)",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern DEZENA_RX = Pattern.compile("\\b(0?\\d|[1-9]\\d)\\b");
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

  public CaixaLotomaniaScraper(PlaywrightConfig config) {
    this.config = config;
  }

  public ScrapedLotomaniaResult scrape() {
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

          List<String> dezenas = findDezenas(page);
          if (dezenas.size() != 20) {
            throw new UpstreamBadResponseException("Dezenas incompletas",
                List.of("Elemento de resultado nao encontrado"));
          }

          return new ScrapedLotomaniaResult(concurso, dataApuracao, dezenas);
        } catch (UpstreamBadResponseException ex) {
          ScrapedLotomaniaResult fallback = fetchFromApi();
          if (fallback != null) {
            return fallback;
          }
          throw ex;
        }
      }
    } catch (TimeoutError ex) {
      throw new UpstreamTimeoutException("Timeout ao consultar resultado oficial da CAIXA", ex);
    } catch (UpstreamBadResponseException ex) {
      throw ex;
    } catch (Exception ex) {
      ScrapedLotomaniaResult fallback = fetchFromApi();
      if (fallback != null) {
        return fallback;
      }
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

  private List<String> findDezenas(Page page) {
    for (String selector : DEZENAS_SELECTORS) {
      List<ElementHandle> elements = page.querySelectorAll(selector);
      List<String> dezenas = parseDezenas(elements);
      if (dezenas.size() == 20) {
        return dezenas;
      }
    }
    return List.of();
  }

  private List<String> parseDezenas(List<ElementHandle> elements) {
    Set<Integer> values = new TreeSet<>();
    for (ElementHandle element : elements) {
      String text = safeText(element);
      if (text.isBlank()) {
        continue;
      }
      Matcher matcher = DEZENA_RX.matcher(text);
      while (matcher.find()) {
        int value = Integer.parseInt(matcher.group(1));
        if (value >= 0 && value <= 99) {
          values.add(value);
        }
      }
    }
    if (values.size() != 20) {
      return List.of();
    }
    List<String> dezenas = new ArrayList<>(20);
    for (int value : values) {
      dezenas.add(String.format("%02d", value));
    }
    return dezenas;
  }

  private ScrapedLotomaniaResult fetchFromApi() {
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(5))
        .build();
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
          .timeout(java.time.Duration.ofSeconds(10))
          .GET()
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return null;
      }
      JsonNode root = MAPPER.readTree(response.body());
      String concurso = textOrNull(root, "numero");
      String dataApuracao = textOrNull(root, "dataApuracao");
      List<String> dezenas = normalizeDezenas(readStringList(root.get("listaDezenas")));
      if (concurso == null || dataApuracao == null || dezenas.size() != 20) {
        return null;
      }
      return new ScrapedLotomaniaResult(concurso, dataApuracao, dezenas);
    } catch (Exception ex) {
      return null;
    }
  }

  private List<String> readStringList(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      String text = item.asText();
      if (text != null && !text.isBlank()) {
        values.add(text.trim());
      }
    }
    return values;
  }

  private List<String> normalizeDezenas(List<String> raw) {
    Set<Integer> values = new TreeSet<>();
    for (String item : raw) {
      if (item == null || item.isBlank()) {
        continue;
      }
      try {
        int value = Integer.parseInt(item.trim());
        if (value >= 0 && value <= 99) {
          values.add(value);
        }
      } catch (NumberFormatException ignored) {
        // skip
      }
    }
    if (values.size() != 20) {
      return List.of();
    }
    List<String> dezenas = new ArrayList<>(20);
    for (int value : values) {
      dezenas.add(String.format("%02d", value));
    }
    return dezenas;
  }

  private String textOrNull(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    String text = value.asText();
    if (text == null || text.isBlank()) {
      return null;
    }
    return text.trim();
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
