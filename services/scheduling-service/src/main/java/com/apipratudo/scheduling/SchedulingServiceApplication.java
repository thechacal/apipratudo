package com.apipratudo.scheduling;

import com.apipratudo.scheduling.config.FirestoreProperties;
import com.apipratudo.scheduling.config.IdempotencyProperties;
import com.apipratudo.scheduling.config.SecurityProperties;
import com.apipratudo.scheduling.config.SchedulingProperties;
import com.apipratudo.scheduling.config.WebhookProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    FirestoreProperties.class,
    IdempotencyProperties.class,
    SecurityProperties.class,
    SchedulingProperties.class,
    WebhookProperties.class
})
public class SchedulingServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(SchedulingServiceApplication.class, args);
  }
}
