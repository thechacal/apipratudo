package com.apipratudo.helpdesk;

import com.apipratudo.helpdesk.config.FirestoreProperties;
import com.apipratudo.helpdesk.config.HelpdeskProperties;
import com.apipratudo.helpdesk.config.HelpdeskStorageProperties;
import com.apipratudo.helpdesk.config.IdempotencyProperties;
import com.apipratudo.helpdesk.config.SecurityProperties;
import com.apipratudo.helpdesk.config.WhatsappProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    FirestoreProperties.class,
    SecurityProperties.class,
    IdempotencyProperties.class,
    HelpdeskProperties.class,
    WhatsappProperties.class,
    HelpdeskStorageProperties.class
})
public class HelpdeskServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(HelpdeskServiceApplication.class, args);
  }
}
