package com.apipratudo.quota.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

  private String adminToken;
  private String internalToken;
  private String portalToken;

  public String getAdminToken() {
    return adminToken;
  }

  public void setAdminToken(String adminToken) {
    this.adminToken = adminToken;
  }

  public String getInternalToken() {
    return internalToken;
  }

  public void setInternalToken(String internalToken) {
    this.internalToken = internalToken;
  }

  public String getPortalToken() {
    return portalToken;
  }

  public void setPortalToken(String portalToken) {
    this.portalToken = portalToken;
  }
}
