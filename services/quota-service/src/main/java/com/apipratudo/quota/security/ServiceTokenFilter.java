package com.apipratudo.quota.security;

import com.apipratudo.quota.config.SecurityProperties;
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

  private static final String ADMIN_HEADER = "X-Admin-Token";
  private static final String INTERNAL_HEADER = "X-Internal-Token";

  private final SecurityProperties properties;
  private final ObjectMapper objectMapper;

  public ServiceTokenFilter(SecurityProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
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
        if (!matchesAdmin(request) && !matchesInternal(request)) {
          unauthorized(response, "Missing or invalid X-Admin-Token or X-Internal-Token");
          return;
        }
      } else if (!matchesInternal(request)) {
        unauthorized(response, "Missing or invalid X-Internal-Token");
        return;
      }
    } else if (path.startsWith("/v1/api-keys")) {
      if (!matchesAdmin(request)) {
        unauthorized(response, "Missing or invalid X-Admin-Token");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private boolean matchesAdmin(HttpServletRequest request) {
    return matchesToken(request.getHeader(ADMIN_HEADER), properties.getAdminToken());
  }

  private boolean matchesInternal(HttpServletRequest request) {
    return matchesToken(request.getHeader(INTERNAL_HEADER), properties.getInternalToken());
  }

  private boolean matchesToken(String value, String expected) {
    return StringUtils.hasText(value) && StringUtils.hasText(expected) && value.equals(expected);
  }

  private void unauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
    ErrorResponse body = new ErrorResponse("UNAUTHORIZED", message, Collections.emptyList());
    objectMapper.writeValue(response.getWriter(), body);
  }
}
