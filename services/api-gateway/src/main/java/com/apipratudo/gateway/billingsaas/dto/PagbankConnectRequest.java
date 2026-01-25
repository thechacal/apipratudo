package com.apipratudo.gateway.billingsaas.dto;

import jakarta.validation.constraints.NotBlank;

public class PagbankConnectRequest {

  @NotBlank
  private String token;

  private String webhookToken;

  private String environment;

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getWebhookToken() {
    return webhookToken;
  }

  public void setWebhookToken(String webhookToken) {
    this.webhookToken = webhookToken;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }
}
