package com.apipratudo.portal.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.gateway")
@Validated
public class GatewayProperties {

  @NotBlank
  private String url = "http://localhost:8080";

  @NotBlank
  private String docsUrl = "http://localhost:8080/docs";

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getDocsUrl() {
    return docsUrl;
  }

  public void setDocsUrl(String docsUrl) {
    this.docsUrl = docsUrl;
  }
}
