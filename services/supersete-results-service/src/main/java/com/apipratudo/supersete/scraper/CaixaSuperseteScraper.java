package com.apipratudo.supersete.scraper;

import com.apipratudo.supersete.config.PlaywrightConfig;
import com.apipratudo.supersete.error.UpstreamBadResponseException;
import com.apipratudo.supersete.error.UpstreamTimeoutException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CaixaSuperseteScraper {

  private static final String URL = "https://loterias.caixa.gov.br/Paginas/Super-Sete.aspx";
  private static final String API_URL = "https://servicebus2.caixa.gov.br/portaldeloterias/api/supersete";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern HEADER_RX = Pattern.compile("Concurso\\s*(\\d+)\\s*\\((\\d{2}/\\d{2}/\\d{4})\\)",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern DIGIT_RX = Pattern.compile("\\b\\d\\b");
  private static final List<String> HEADER_SELECTORS = List.of(
      "h2",
      ".titulo-modalidade",
      "#wp_resultados h2"
  );
  private static final List<String> COLUNAS_SELECTORS = List.of(
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

  public CaixaSuperseteScraper(PlaywrightConfig config) {
    this.config = config;
  }

  public ScrapedSuperseteResult scrape() {
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

          List<String> colunas = findColunas(page);
          if (colunas.size() != 7) {
            throw new UpstreamBadResponseException("Colunas incompletas",
                List.of("Elemento de resultado nao encontrado"));
          }

          return new ScrapedSuperseteResult(concurso, dataApuracao, colunas);
        }
      } catch (UpstreamBadResponseException ex) {
        ScrapedSuperseteResult fallback = fetchFromApi();
        if (fallback != null) {
          return fallback;
        }
        throw ex;
      }
    } catch (TimeoutError ex) {
      throw new UpstreamTimeoutException("Timeout ao consultar resultado oficial da CAIXA", ex);
    } catch (UpstreamBadResponseException ex) {
      throw ex;
    } catch (Exception ex) {
      ScrapedSuperseteResult fallback = fetchFromApi();
      if (fallback != null) {
        return fallback;
      }
      throw new UpstreamBadResponseException("Falha ao consultar resultado oficial da CAIXA",
          List.of("Elemento de resultado nao encontrado"));
    }
  }

  private void waitForReady(Page page) {
    for (Frame frame : frames(page)) {
      for (String selector : READY_HINTS) {
        try {
          frame.waitForSelector(selector, new Frame.WaitForSelectorOptions()
              .setTimeout((double) config.getTimeoutMs()));
          return;
        } catch (Exception ignored) {
          // try next
        }
      }
    }
    throw new UpstreamBadResponseException("Elemento de resultado nao encontrado",
        List.of("Elemento de resultado nao encontrado"));
  }

  private String findHeader(Page page) {
    for (Frame frame : frames(page)) {
      for (String selector : HEADER_SELECTORS) {
        try {
          String text = frame.locator(selector).first().innerText();
          if (text != null && !text.isBlank()) {
            return text.trim();
          }
        } catch (Exception ignored) {
          // try next
        }
      }
      String body = safeInnerText(frame, "body");
      if (body != null && !body.isBlank()) {
        Matcher matcher = HEADER_RX.matcher(body);
        if (matcher.find()) {
          return matcher.group(0);
        }
      }
    }
    return null;
  }

  private List<String> findColunas(Page page) {
    for (Frame frame : frames(page)) {
      for (String selector : COLUNAS_SELECTORS) {
        List<ElementHandle> elements = frame.querySelectorAll(selector);
        List<String> colunas = parseColunas(elements);
        if (colunas.size() == 7) {
          return colunas;
        }
      }
    }
    return List.of();
  }

  private List<String> parseColunas(List<ElementHandle> elements) {
    List<String> valores = new ArrayList<>();
    for (ElementHandle element : elements) {
      String text = safeText(element);
      if (text.isBlank()) {
        continue;
      }
      Matcher matcher = DIGIT_RX.matcher(text);
      while (matcher.find()) {
        valores.add(matcher.group());
      }
    }
    if (valores.size() != 7) {
      return List.of();
    }
    return valores;
  }

  private ScrapedSuperseteResult fetchFromApi() {
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
          .timeout(Duration.ofSeconds(10))
          .GET()
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return null;
      }
      JsonNode root = MAPPER.readTree(response.body());
      String concurso = textOrNull(root, "numero");
      String dataApuracao = textOrNull(root, "dataApuracao");
      List<String> colunas = normalizeColunas(readStringList(root.get("listaDezenas")));
      if (concurso == null || dataApuracao == null || colunas.size() != 7) {
        return null;
      }
      return new ScrapedSuperseteResult(concurso, dataApuracao, colunas);
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

  private List<String> normalizeColunas(List<String> raw) {
    if (raw.size() != 7) {
      return List.of();
    }
    List<String> colunas = new ArrayList<>(7);
    for (String item : raw) {
      String value = item == null ? "" : item.trim();
      if (value.length() != 1 || !Character.isDigit(value.charAt(0))) {
        return List.of();
      }
      colunas.add(value);
    }
    return colunas;
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

  private String safeInnerText(Frame frame, String selector) {
    if (frame == null) {
      return null;
    }
    try {
      return frame.innerText(selector);
    } catch (Exception ex) {
      return null;
    }
  }

  private List<Frame> frames(Page page) {
    return page.frames();
  }
}
