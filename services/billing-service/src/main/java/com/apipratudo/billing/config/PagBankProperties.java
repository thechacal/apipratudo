package com.apipratudo.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pagbank")
public class PagBankProperties {

  private String baseUrl = "https://sandbox.api.pagseguro.com";
  private String token;
  private String webhookToken;
  private String notificationUrl;
  private int qrTtlSeconds = 300;
  private String timezone = "America/Sao_Paulo";
  private boolean webhookStrict = true;
  private int timeoutMs = 60000;
  private final Customer customer = new Customer();
  private final Shipping shipping = new Shipping();

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

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

  public String getNotificationUrl() {
    return notificationUrl;
  }

  public void setNotificationUrl(String notificationUrl) {
    this.notificationUrl = notificationUrl;
  }

  public int getQrTtlSeconds() {
    return qrTtlSeconds;
  }

  public void setQrTtlSeconds(int qrTtlSeconds) {
    this.qrTtlSeconds = qrTtlSeconds;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public boolean isWebhookStrict() {
    return webhookStrict;
  }

  public void setWebhookStrict(boolean webhookStrict) {
    this.webhookStrict = webhookStrict;
  }

  public int getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(int timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public Customer getCustomer() {
    return customer;
  }

  public Shipping getShipping() {
    return shipping;
  }

  public static class Customer {
    private String name = "Cliente";
    private String email = "cliente@exemplo.com";
    private String taxId = "12345678909";
    private String phoneArea = "11";
    private String phoneNumber = "999999999";

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getTaxId() {
      return taxId;
    }

    public void setTaxId(String taxId) {
      this.taxId = taxId;
    }

    public String getPhoneArea() {
      return phoneArea;
    }

    public void setPhoneArea(String phoneArea) {
      this.phoneArea = phoneArea;
    }

    public String getPhoneNumber() {
      return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
      this.phoneNumber = phoneNumber;
    }
  }

  public static class Shipping {
    private String postalCode;
    private String street = "Rua Exemplo";
    private String number = "100";
    private String locality = "Centro";
    private String city = "Sao Paulo";
    private String region = "SP";
    private String country = "BRA";

    public String getPostalCode() {
      return postalCode;
    }

    public void setPostalCode(String postalCode) {
      this.postalCode = postalCode;
    }

    public String getStreet() {
      return street;
    }

    public void setStreet(String street) {
      this.street = street;
    }

    public String getNumber() {
      return number;
    }

    public void setNumber(String number) {
      this.number = number;
    }

    public String getLocality() {
      return locality;
    }

    public void setLocality(String locality) {
      this.locality = locality;
    }

    public String getCity() {
      return city;
    }

    public void setCity(String city) {
      this.city = city;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getCountry() {
      return country;
    }

    public void setCountry(String country) {
      this.country = country;
    }
  }
}
