package com.apipratudo.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class DeliveryDispatchConfig {

  @Bean
  public TaskScheduler deliveryTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(4);
    scheduler.setThreadNamePrefix("delivery-dispatch-");
    scheduler.initialize();
    return scheduler;
  }

  @Bean
  public WebClient deliveryWebClient() {
    return WebClient.builder().build();
  }
}
