package com.apipratudo.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DeveloperPortalServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(DeveloperPortalServiceApplication.class, args);
  }
}
