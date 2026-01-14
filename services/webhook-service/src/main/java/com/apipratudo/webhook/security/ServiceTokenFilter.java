package com.apipratudo.webhook.security;

import com.apipratudo.webhook.config.SecurityProperties;
import com.apipratudo.webhook.error.ErrorResponse;
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

  private static final String SERVICE_HEADER = "X-Service-Token";

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
        || path.equals("/openapi.yaml")
        || !path.startsWith("/internal/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    if (!matchesToken(request.getHeader(SERVICE_HEADER), properties.getServiceToken())) {
      unauthorized(response, "Missing or invalid X-Service-Token");
      return;
    }
    filterChain.doFilter(request, response);
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
