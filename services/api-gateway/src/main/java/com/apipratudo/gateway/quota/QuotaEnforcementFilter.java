package com.apipratudo.gateway.quota;

import com.apipratudo.gateway.error.ErrorResponse;
import com.apipratudo.gateway.logging.TraceIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
    return "/v1/echo".equals(path);
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

    String traceId = TraceIdUtils.resolveTraceId(request);
    String requestId = StringUtils.hasText(traceId) ? traceId : UUID.randomUUID().toString();
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
      filterChain.doFilter(request, response);
      return;
    }

    if (result.statusCode() == HttpServletResponse.SC_UNAUTHORIZED) {
      writeError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
          reasonMessage(result.reason(), "Invalid API key"));
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

  private String reasonMessage(String reason, String fallback) {
    if (!StringUtils.hasText(reason)) {
      return fallback;
    }
    return fallback + " (" + reason + ")";
  }

  private void writeError(
      HttpServletResponse response,
      HttpServletRequest request,
      int status,
      String error,
      String message
  ) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ErrorResponse body = new ErrorResponse(error, message, Collections.emptyList(),
        TraceIdUtils.resolveTraceId(request));
    objectMapper.writeValue(response.getWriter(), body);
  }
}
