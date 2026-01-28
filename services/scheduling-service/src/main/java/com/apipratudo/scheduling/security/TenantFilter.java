package com.apipratudo.scheduling.security;

import com.apipratudo.scheduling.error.ErrorResponse;
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
@Order(2)
public class TenantFilter extends OncePerRequestFilter {

  private static final String TENANT_HEADER = "X-Tenant-Id";

  private final ObjectMapper objectMapper;

  public TenantFilter(ObjectMapper objectMapper) {
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
    String tenantId = request.getHeader(TENANT_HEADER);
    if (!StringUtils.hasText(tenantId)) {
      missingTenant(response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private void missingTenant(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
    ErrorResponse body = new ErrorResponse("MISSING_TENANT", "Missing X-Tenant-Id", Collections.emptyList(), null);
    objectMapper.writeValue(response.getWriter(), body);
  }
}
