package com.apipratudo.gateway.quota;

import com.apipratudo.gateway.error.ErrorResponse;
import com.apipratudo.gateway.logging.TraceIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class QuotaEnforcementFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(QuotaEnforcementFilter.class);
  private static final String API_KEY_HEADER = "X-Api-Key";
  private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String KEYS_REQUEST_PATH = "/v1/keys/request";
  private static final String KEYS_STATUS_PATH = "/v1/keys/status";
  private static final String KEYS_UPGRADE_PREFIX = "/v1/keys/upgrade";

  private final QuotaClient quotaClient;
  private final ObjectMapper objectMapper;

  public QuotaEnforcementFilter(QuotaClient quotaClient, ObjectMapper objectMapper) {
    this.quotaClient = quotaClient;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    if (!path.startsWith("/v1")) {
      return true;
    }
    if ("/v1/echo".equals(path)) {
      return true;
    }
    return path.startsWith(KEYS_REQUEST_PATH);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String apiKey = request.getHeader(API_KEY_HEADER);
    if (!StringUtils.hasText(apiKey)) {
      writeError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
          "Missing X-Api-Key");
      return;
    }

    String path = request.getRequestURI();
    if (isKeysStatus(path) || isKeysUpgrade(path)) {
      filterChain.doFilter(request, response);
      return;
    }

    String traceId = TraceIdUtils.resolveTraceId(request);
    String requestId = resolveRequestId(request);
    String route = request.getMethod() + " " + request.getRequestURI();

    QuotaClientResult result;
    try {
      result = quotaClient.consume(apiKey, requestId, route, 1, traceId);
    } catch (Exception ex) {
      log.warn("Quota service unavailable route={} traceId={} error={}", route, traceId, ex.getMessage());
      writeError(response, request, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "QUOTA_UNAVAILABLE",
          "Quota service unavailable");
      return;
    }

    if (result.allowed()) {
      StatusCaptureResponseWrapper wrapped = new StatusCaptureResponseWrapper(response);
      boolean shouldRefund = false;
      try {
        filterChain.doFilter(request, wrapped);
      } catch (Exception ex) {
        shouldRefund = true;
        throw ex;
      } finally {
        if (shouldRefund || wrapped.getStatus() >= 500) {
          tryRefund(apiKey, requestId, traceId, route);
        }
      }
      return;
    }

    if (isQuotaExceeded(result)) {
      writeQuotaExceeded(response, request, result.plan());
      return;
    }

    if (result.statusCode() == HttpServletResponse.SC_UNAUTHORIZED
        || result.statusCode() == HttpServletResponse.SC_FORBIDDEN) {
      if ("INVALID_KEY".equals(result.reason())) {
        writeError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
            reasonMessage(result.reason(), "Invalid API key"));
        return;
      }
      writeError(response, request, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "QUOTA_AUTH_MISCONFIGURED",
          "Quota auth misconfigured");
      return;
    }

    if (result.statusCode() == 429) {
      writeError(response, request, 429, "RATE_LIMITED",
          reasonMessage(result.reason(), "Rate limit exceeded"));
      return;
    }

    writeError(response, request, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "QUOTA_UNAVAILABLE",
        "Quota service rejected the request");
  }

  private boolean isKeysStatus(String path) {
    return KEYS_STATUS_PATH.equals(path);
  }

  private boolean isKeysUpgrade(String path) {
    return path.startsWith(KEYS_UPGRADE_PREFIX);
  }

  private boolean isQuotaExceeded(QuotaClientResult result) {
    if (result == null) {
      return false;
    }
    if (result.statusCode() == HttpServletResponse.SC_PAYMENT_REQUIRED) {
      return true;
    }
    if ("QUOTA_EXCEEDED".equals(result.reason())) {
      return true;
    }
    return "QUOTA_EXCEEDED".equals(result.error());
  }

  private void tryRefund(String apiKey, String requestId, String traceId, String route) {
    try {
      quotaClient.refund(apiKey, requestId, traceId);
    } catch (Exception ex) {
      log.warn("Quota refund failed route={} requestId={} traceId={} error={}", route, requestId, traceId,
          ex.getMessage());
    }
  }

  private String resolveRequestId(HttpServletRequest request) {
    if (allowsIdempotencyKey(request.getMethod())) {
      String idempotency = request.getHeader(IDEMPOTENCY_KEY_HEADER);
      if (StringUtils.hasText(idempotency)) {
        return idempotency.trim();
      }
    }
    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (StringUtils.hasText(requestId)) {
      return requestId.trim();
    }
    return UUID.randomUUID().toString();
  }

  private boolean allowsIdempotencyKey(String method) {
    return !("GET".equalsIgnoreCase(method)
        || "HEAD".equalsIgnoreCase(method)
        || "OPTIONS".equalsIgnoreCase(method));
  }

  private String reasonMessage(String reason, String fallback) {
    if (!StringUtils.hasText(reason)) {
      return fallback;
    }
    return fallback + " (" + reason + ")";
  }

  private void writeQuotaExceeded(
      HttpServletResponse response,
      HttpServletRequest request,
      String plan
  ) throws IOException {
    String message = "Cota esgotada. Recarregue para continuar.";
    QuotaExceededResponse body = new QuotaExceededResponse(
        "QUOTA_EXCEEDED",
        message,
        new QuotaExceededResponse.Upgrade("/v1/keys/upgrade", "/docs")
    );

    response.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
    objectMapper.writeValue(response.getWriter(), body);
  }

  private void writeError(
      HttpServletResponse response,
      HttpServletRequest request,
      int status,
      String error,
      String message
  ) throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
    ErrorResponse body = new ErrorResponse(error, message, Collections.emptyList(),
        TraceIdUtils.resolveTraceId(request));
    objectMapper.writeValue(response.getWriter(), body);
  }

  private static class StatusCaptureResponseWrapper extends HttpServletResponseWrapper {
    private int httpStatus = HttpServletResponse.SC_OK;

    StatusCaptureResponseWrapper(HttpServletResponse response) {
      super(response);
    }

    @Override
    public void setStatus(int sc) {
      this.httpStatus = sc;
      super.setStatus(sc);
    }

    @Override
    public void sendError(int sc) throws IOException {
      this.httpStatus = sc;
      super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      this.httpStatus = sc;
      super.sendError(sc, msg);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
      this.httpStatus = HttpServletResponse.SC_FOUND;
      super.sendRedirect(location);
    }

    @Override
    public int getStatus() {
      return httpStatus;
    }
  }
}
