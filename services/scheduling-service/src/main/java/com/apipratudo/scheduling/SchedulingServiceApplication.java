package com.apipratudo.scheduling;

import com.apipratudo.scheduling.config.AgendaCreditsProperties;
import com.apipratudo.scheduling.config.BillingSaasProperties;
import com.apipratudo.scheduling.config.FirestoreProperties;
import com.apipratudo.scheduling.config.IdempotencyProperties;
import com.apipratudo.scheduling.config.NoShowProperties;
import com.apipratudo.scheduling.config.SecurityProperties;
import com.apipratudo.scheduling.config.SchedulingProperties;
import com.apipratudo.scheduling.config.WebhookProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    AgendaCreditsProperties.class,
    BillingSaasProperties.class,
    FirestoreProperties.class,
    IdempotencyProperties.class,
    NoShowProperties.class,
    SecurityProperties.class,
    SchedulingProperties.class,
    WebhookProperties.class
})
public class SchedulingServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(SchedulingServiceApplication.class, args);
  }
}
