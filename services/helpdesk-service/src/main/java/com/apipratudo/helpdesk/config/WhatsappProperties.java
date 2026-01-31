package com.apipratudo.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsappProperties {

  private String verifyToken;
  private String appSecret;
  private String bindingsCollection = "whatsapp_bindings";

  public String getVerifyToken() {
    return verifyToken;
  }

  public void setVerifyToken(String verifyToken) {
    this.verifyToken = verifyToken;
  }

  public String getAppSecret() {
    return appSecret;
  }

  public void setAppSecret(String appSecret) {
    this.appSecret = appSecret;
  }

  public String getBindingsCollection() {
    return bindingsCollection;
  }

  public void setBindingsCollection(String bindingsCollection) {
    this.bindingsCollection = bindingsCollection;
  }
}
