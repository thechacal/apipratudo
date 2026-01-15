package com.apipratudo.timemania.scraper;

import com.apipratudo.timemania.config.PlaywrightConfig;
import com.apipratudo.timemania.error.UpstreamBadResponseException;
import com.apipratudo.timemania.error.UpstreamTimeoutException;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CaixaTimemaniaScraper {

  private static final String URL = "https://loterias.caixa.gov.br/Paginas/Timemania.aspx";
  private static final String API_URL = "https://servicebus2.caixa.gov.br/portaldeloterias/api/timemania";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern HEADER_RX = Pattern.compile(
      "Concurso\\s*(\\d+)\\s*\\((\\d{2}/\\d{2}/\\d{4})\\)",
      Pattern.CASE_INSENSITIVE
  );
  private static final Pattern DEZENA_RX = Pattern.compile("\\b(0?[1-9]|[1-7]\\d|80)\\b");
  private static final Pattern TIME_CORACAO_RX = Pattern.compile(
      "Time do cora(?:\\u00e7\\u00e3|ca)o\\s*:?\\s*([\\p{L}\\d .'-]+)",
      Pattern.CASE_INSENSITIVE
  );
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

  public CaixaTimemaniaScraper(PlaywrightConfig config) {
    this.config = config;
  }

  public ScrapedTimemaniaResult scrape() {
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

          try {
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
            if (dezenas.size() != 7) {
              throw new UpstreamBadResponseException("Dezenas incompletas",
                  List.of("Elemento de resultado nao encontrado"));
            }

            String timeCoracao = findTimeCoracao(page);
            if (timeCoracao == null || timeCoracao.isBlank()) {
              throw new UpstreamBadResponseException("Time do coracao nao encontrado",
                  List.of("Elemento de resultado nao encontrado"));
            }

            return new ScrapedTimemaniaResult(concurso, dataApuracao, dezenas, timeCoracao);
          } catch (UpstreamBadResponseException ex) {
            ScrapedTimemaniaResult fallback = fetchFromApi();
            if (fallback != null) {
              return fallback;
            }
            throw ex;
          }
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

  private List<String> findDezenas(Page page) {
    for (Frame frame : frames(page)) {
      for (String selector : DEZENAS_SELECTORS) {
        List<ElementHandle> elements = frame.querySelectorAll(selector);
        List<String> dezenas = parseDezenas(elements);
        if (dezenas.size() == 7) {
          return dezenas;
        }
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
        if (value >= 1 && value <= 80) {
          values.add(value);
        }
      }
    }
    if (values.size() != 7) {
      return List.of();
    }
    List<String> dezenas = new ArrayList<>(7);
    for (int value : values) {
      dezenas.add(String.format("%02d", value));
    }
    return dezenas;
  }

  private String findTimeCoracao(Page page) {
    for (Frame frame : frames(page)) {
      try {
        String body = safeInnerText(frame, "body");
        if (body == null || body.isBlank()) {
          continue;
        }
        Matcher matcher = TIME_CORACAO_RX.matcher(body);
        if (matcher.find()) {
          String raw = matcher.group(1).trim();
          int lineBreak = raw.indexOf('\n');
          String value = lineBreak > 0 ? raw.substring(0, lineBreak).trim() : raw;
          if (hasLettersOrDigits(value)) {
            return value;
          }
        }
      } catch (Exception ignored) {
        // ignore
      }
    }
    return null;
  }

  private ScrapedTimemaniaResult fetchFromApi() {
    HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
          .timeout(Duration.ofSeconds(10))
          .header("User-Agent", "Mozilla/5.0")
          .header("Accept", "application/json, text/plain, */*")
          .header("Referer", "https://loterias.caixa.gov.br/")
          .GET()
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        System.out.println("Timemania fallback API status=" + response.statusCode());
        return null;
      }

      JsonNode root = MAPPER.readTree(response.body());
      String concurso = textOrNull(root, "numero");
      String dataApuracao = textOrNull(root, "dataApuracao");
      List<String> dezenas = normalizeDezenas(readStringList(root.get("listaDezenas")));
      String timeCoracao = normalizeTimeCoracao(textOrNull(root, "nomeTimeCoracaoMesSorte"));

      if (concurso == null || dataApuracao == null || dezenas.size() != 7 || timeCoracao == null) {
        return null;
      }
      return new ScrapedTimemaniaResult(concurso, dataApuracao, dezenas, timeCoracao);
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
        values.add(text);
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
        if (value >= 1 && value <= 80) {
          values.add(value);
        }
      } catch (NumberFormatException ignored) {
        // skip
      }
    }
    if (values.size() != 7) {
      return List.of();
    }
    List<String> dezenas = new ArrayList<>(7);
    for (int value : values) {
      dezenas.add(String.format("%02d", value));
    }
    return dezenas;
  }

  private String normalizeTimeCoracao(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.replaceAll("\\s+", " ").trim();
    return hasLettersOrDigits(normalized) ? normalized : null;
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
    return text;
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

  private boolean hasLettersOrDigits(String value) {
    if (value == null) {
      return false;
    }
    return value.chars().anyMatch(ch -> Character.isLetterOrDigit(ch));
  }

  private List<Frame> frames(Page page) {
    return page.frames();
  }
}
