package com.apipratudo.federal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FederalResultsServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(FederalResultsServiceApplication.class, args);
  }
}
