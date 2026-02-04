package com.apipratudo.identity;

import com.apipratudo.identity.config.IdempotencyProperties;
import com.apipratudo.identity.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    SecurityProperties.class,
    IdempotencyProperties.class
})
public class IdentityVerifyServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(IdentityVerifyServiceApplication.class, args);
  }
}
