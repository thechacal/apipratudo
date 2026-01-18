package com.apipratudo.quota.security;

import com.apipratudo.quota.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ServiceTokenFilter extends OncePerRequestFilter {

  private static final String API_KEY_HEADER = "X-Api-Key";

  private final SecurityTokenService tokenService;
  private final ObjectMapper objectMapper;

  public ServiceTokenFilter(SecurityTokenService tokenService, ObjectMapper objectMapper) {
    this.tokenService = tokenService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return "OPTIONS".equalsIgnoreCase(request.getMethod())
        || path.startsWith("/actuator")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/swagger-ui")
        || path.equals("/docs")
        || path.equals("/openapi.yaml");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String path = request.getRequestURI();

    if (path.startsWith("/v1/quota/")) {
      if (path.equals("/v1/quota/status")) {
        if (!tokenService.isAdmin(request) && !tokenService.isInternal(request)
            && !StringUtils.hasText(request.getHeader(API_KEY_HEADER))) {
          unauthorized(response, "Missing or invalid X-Api-Key or admin/internal token");
          return;
        }
      } else if (!tokenService.isInternal(request)) {
        unauthorized(response, "Missing or invalid X-Internal-Token");
        return;
      }
    } else if (path.startsWith("/v1/api-keys")) {
      if (!tokenService.isAdmin(request)) {
        unauthorized(response, "Missing or invalid X-Admin-Token");
        return;
      }
    } else if (path.startsWith("/v1/internal/keys")) {
      if (!tokenService.isPortal(request) && !tokenService.isInternal(request)) {
        unauthorized(response, "Missing or invalid X-Portal-Token");
        return;
      }
    } else if (path.startsWith("/v1/subscriptions/")) {
      if (!tokenService.isAdmin(request) && !tokenService.isInternal(request)) {
        unauthorized(response, "Missing or invalid X-Admin-Token or X-Internal-Token");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private void unauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
    ErrorResponse body = new ErrorResponse("UNAUTHORIZED", message, Collections.emptyList());
    objectMapper.writeValue(response.getWriter(), body);
  }
}
