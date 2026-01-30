package com.apipratudo.scheduling.service;

import com.apipratudo.scheduling.config.NoShowProperties;
import com.apipratudo.scheduling.repository.SchedulingStore;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NoShowScheduler {

  private static final Logger log = LoggerFactory.getLogger(NoShowScheduler.class);

  private final SchedulingService schedulingService;
  private final SchedulingStore store;
  private final NoShowProperties properties;

  public NoShowScheduler(
      SchedulingService schedulingService,
      SchedulingStore store,
      NoShowProperties properties
  ) {
    this.schedulingService = schedulingService;
    this.store = store;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${scheduling.no-show.scan-interval-ms:60000}")
  public void run() {
    if (!properties.isEnabled()) {
      return;
    }
    try {
      Set<String> tenants = store.listTenants();
      for (String tenantId : tenants) {
        schedulingService.processNoShows(tenantId);
      }
    } catch (Exception ex) {
      log.warn("No-show scan failed error={}", ex.getMessage());
    }
  }
}
