package com.apipratudo.quota;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QuotaServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(QuotaServiceApplication.class, args);
  }
}
