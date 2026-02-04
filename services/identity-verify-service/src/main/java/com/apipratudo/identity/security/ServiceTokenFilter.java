package com.apipratudo.identity.security;

import com.apipratudo.identity.config.SecurityProperties;
import com.apipratudo.identity.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
public class ServiceTokenFilter extends OncePerRequestFilter {

  private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

  private final SecurityProperties securityProperties;
  private final ObjectMapper objectMapper;

  public ServiceTokenFilter(SecurityProperties securityProperties, ObjectMapper objectMapper) {
    this.securityProperties = securityProperties;
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
    String token = request.getHeader(SERVICE_TOKEN_HEADER);
    String expected = securityProperties.getServiceToken();
    if (!StringUtils.hasText(expected) || !expected.equals(token)) {
      unauthorized(response, "Missing or invalid X-Service-Token");
      return;
    }

    filterChain.doFilter(request, response);
  }

  private void unauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
    ErrorResponse body = new ErrorResponse("UNAUTHORIZED", message, Collections.emptyList(), null);
    objectMapper.writeValue(response.getWriter(), body);
  }
}
