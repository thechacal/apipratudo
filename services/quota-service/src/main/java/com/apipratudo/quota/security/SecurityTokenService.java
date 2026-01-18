package com.apipratudo.quota.security;

import com.apipratudo.quota.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecurityTokenService {

  private static final String ADMIN_HEADER = "X-Admin-Token";
  private static final String INTERNAL_HEADER = "X-Internal-Token";
  private static final String PORTAL_HEADER = "X-Portal-Token";

  private final SecurityProperties properties;

  public SecurityTokenService(SecurityProperties properties) {
    this.properties = properties;
  }

  public boolean isAdmin(HttpServletRequest request) {
    return matches(request.getHeader(ADMIN_HEADER), properties.getAdminToken());
  }

  public boolean isInternal(HttpServletRequest request) {
    return matches(request.getHeader(INTERNAL_HEADER), properties.getInternalToken());
  }

  public boolean isPortal(HttpServletRequest request) {
    return matches(request.getHeader(PORTAL_HEADER), properties.getPortalToken());
  }

  private boolean matches(String value, String expected) {
    return StringUtils.hasText(value) && StringUtils.hasText(expected) && value.equals(expected);
  }
}
