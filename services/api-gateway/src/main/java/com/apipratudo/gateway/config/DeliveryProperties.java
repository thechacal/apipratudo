package com.apipratudo.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.deliveries")
public class DeliveryProperties {

  private String collection = "deliveries";
  private int listLimit = 200;

  public String getCollection() {
    return collection;
  }

  public void setCollection(String collection) {
    this.collection = collection;
  }

  public int getListLimit() {
    return listLimit;
  }

  public void setListLimit(int listLimit) {
    this.listLimit = listLimit;
  }
}
