package com.apipratudo.billingsaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BillingSaasServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(BillingSaasServiceApplication.class, args);
  }
}
