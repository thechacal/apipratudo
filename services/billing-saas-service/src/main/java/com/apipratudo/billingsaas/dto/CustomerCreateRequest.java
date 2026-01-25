package com.apipratudo.billingsaas.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public class CustomerCreateRequest {

  @NotBlank
  private String name;

  @NotBlank
  private String document;

  @NotBlank
  private String email;

  @NotBlank
  private String phone;

  private String externalId;

  private Map<String, String> metadata;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDocument() {
    return document;
  }

  public void setDocument(String document) {
    this.document = document;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }
}
